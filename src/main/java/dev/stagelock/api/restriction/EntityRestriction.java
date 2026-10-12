package dev.stagelock.api.restriction;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.stagelock.api.StageRequirement;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.world.entity.EntityType;

import java.util.List;
import java.util.Optional;

/**
 * {@code "type": "stagelock:entity"} - gates attacking, interacting with, and spawning of entities.
 *
 * <pre>{@code
 * {
 *   "type": "stagelock:entity",
 *   "requires": "mypack:endgame",
 *   "entities": ["minecraft:wither", "#minecraft:raiders"],
 *   "actions": { "attack": true, "interact": true, "spawn": { "range": 128, "allow_spawners": true } }
 * }
 * }</pre>
 * With {@code spawn}, the entity only spawns naturally if a player within {@code range} blocks meets the requirement.
 */
public record EntityRestriction(
        StageRequirement requires,
        List<RegistryMatcher<EntityType<?>>> entities,
        Actions actions,
        Optional<Component> message
) implements Restriction {

    public static final MapCodec<EntityRestriction> MAP_CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            StageRequirement.CODEC.fieldOf("requires").forGetter(EntityRestriction::requires),
            RegistryMatcher.listCodec(Registries.ENTITY_TYPE).fieldOf("entities").forGetter(EntityRestriction::entities),
            Actions.CODEC.optionalFieldOf("actions", Actions.DEFAULT).forGetter(EntityRestriction::actions),
            ComponentSerialization.CODEC.optionalFieldOf("message").forGetter(EntityRestriction::message)
    ).apply(i, EntityRestriction::new));

    @Override
    public RestrictionType<?> type() {
        return RestrictionTypes.ENTITY;
    }

    public boolean matches(EntityType<?> type) {
        return RegistryMatcher.anyMatch(entities, type.builtInRegistryHolder());
    }

    public record Spawn(int range, boolean allowSpawners) {
        public static final Codec<Spawn> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.intRange(1, 1024).optionalFieldOf("range", 128).forGetter(Spawn::range),
                Codec.BOOL.optionalFieldOf("allow_spawners", true).forGetter(Spawn::allowSpawners)
        ).apply(i, Spawn::new));
    }

    public record Actions(boolean attack, boolean interact, Optional<Spawn> spawn) {

        public static final Actions DEFAULT = new Actions(true, true, Optional.empty());

        public static final Codec<Actions> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.BOOL.optionalFieldOf("attack", true).forGetter(Actions::attack),
                Codec.BOOL.optionalFieldOf("interact", true).forGetter(Actions::interact),
                Spawn.CODEC.optionalFieldOf("spawn").forGetter(Actions::spawn)
        ).apply(i, Actions::new));
    }
}
