package dev.stagelock.api;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;

import java.util.List;
import java.util.Optional;

/**
 * Optional metadata for a stage, from {@code data/<ns>/stagelock/stages/<path>.json}. The stage id is
 * {@code <ns>:<path>} unless the file sets {@code "id"}. Stages work without a definition; a definition
 * adds a display name, prerequisites, automatic unlocks and grant effects.
 *
 * <pre>{@code
 * {
 *   "id": "iron_age",
 *   "display_name": {"translate": "stage.mypack.iron_age"},
 *   "description": "Smelt your first iron.",
 *   "icon": "minecraft:iron_ingot",
 *   "requires": ["stone_age"],
 *   "unlock": {"advancements": ["minecraft:story/smelt_iron"], "scope": "player"},
 *   "on_grant": {"message": true, "function": "mypack:iron_age_unlocked"}
 * }
 * }</pre>
 */
public record StageDefinition(
        Optional<String> id,
        Optional<Component> displayName,
        Optional<Component> description,
        Optional<ResourceLocation> icon,
        List<String> requires,
        Unlock unlock,
        OnGrant onGrant
) {

    public static final Codec<StageDefinition> CODEC = RecordCodecBuilder.create(i -> i.group(
            StageIds.CODEC.optionalFieldOf("id").forGetter(StageDefinition::id),
            ComponentSerialization.CODEC.optionalFieldOf("display_name").forGetter(StageDefinition::displayName),
            ComponentSerialization.CODEC.optionalFieldOf("description").forGetter(StageDefinition::description),
            // Kept as an id: an icon from a mod that isn't installed must not discard the whole definition.
            ResourceLocation.CODEC.optionalFieldOf("icon").forGetter(StageDefinition::icon),
            StageIds.CODEC.listOf().optionalFieldOf("requires", List.of()).forGetter(StageDefinition::requires),
            Unlock.CODEC.optionalFieldOf("unlock", Unlock.NONE).forGetter(StageDefinition::unlock),
            OnGrant.CODEC.optionalFieldOf("on_grant", OnGrant.DEFAULT).forGetter(StageDefinition::onGrant)
    ).apply(i, StageDefinition::new));

    /** The icon item, if it names an item that exists. */
    public Optional<Item> iconItem() {
        return icon.flatMap(BuiltInRegistries.ITEM::getOptional);
    }

    public static StageDefinition simple(Component displayName) {
        return new StageDefinition(Optional.empty(), Optional.of(displayName), Optional.empty(), Optional.empty(),
                List.of(), Unlock.NONE, OnGrant.DEFAULT);
    }

    public record Unlock(List<ResourceLocation> advancements, StageScope scope) {

        public static final Unlock NONE = new Unlock(List.of(), StageScope.PLAYER);

        public static final Codec<Unlock> CODEC = RecordCodecBuilder.create(i -> i.group(
                ResourceLocation.CODEC.listOf().optionalFieldOf("advancements", List.of()).forGetter(Unlock::advancements),
                StageScope.CODEC.optionalFieldOf("scope", StageScope.PLAYER).forGetter(Unlock::scope)
        ).apply(i, Unlock::new));
    }

    public record OnGrant(boolean message, Optional<ResourceLocation> function) {

        public static final OnGrant DEFAULT = new OnGrant(false, Optional.empty());

        public static final MapCodec<OnGrant> MAP_CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
                Codec.BOOL.optionalFieldOf("message", false).forGetter(OnGrant::message),
                ResourceLocation.CODEC.optionalFieldOf("function").forGetter(OnGrant::function)
        ).apply(i, OnGrant::new));
        public static final Codec<OnGrant> CODEC = MAP_CODEC.codec();
    }
}
