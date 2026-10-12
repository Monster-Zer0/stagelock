package dev.stagelock.api.restriction;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.stagelock.api.StageRequirement;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.world.item.enchantment.Enchantment;

import java.util.List;
import java.util.Optional;

/**
 * {@code "type": "stagelock:enchantment"} - any item carrying a matching enchantment is treated as a
 * restricted item (same {@code actions} as {@code stagelock:item}). Enchanted books count too unless
 * {@code check_books} is false.
 *
 * <pre>{@code
 * { "type": "stagelock:enchantment", "requires": "mypack:arcane", "enchantments": ["minecraft:mending"],
 *   "actions": { "hold": "allow", "viewer": "show" } }
 * }</pre>
 */
public record EnchantmentRestriction(
        StageRequirement requires,
        List<RegistryMatcher<Enchantment>> enchantments,
        boolean checkBooks,
        ItemRestriction.Actions actions,
        Optional<Component> message
) implements Restriction {

    public static final MapCodec<EnchantmentRestriction> MAP_CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            StageRequirement.CODEC.fieldOf("requires").forGetter(EnchantmentRestriction::requires),
            RegistryMatcher.listCodec(Registries.ENCHANTMENT).fieldOf("enchantments").forGetter(EnchantmentRestriction::enchantments),
            Codec.BOOL.optionalFieldOf("check_books", true).forGetter(EnchantmentRestriction::checkBooks),
            ItemRestriction.Actions.CODEC.optionalFieldOf("actions", ItemRestriction.Actions.ALL).forGetter(EnchantmentRestriction::actions),
            ComponentSerialization.CODEC.optionalFieldOf("message").forGetter(EnchantmentRestriction::message)
    ).apply(i, EnchantmentRestriction::new));

    @Override
    public RestrictionType<?> type() {
        return RestrictionTypes.ENCHANTMENT;
    }

    /** The equivalent item rule, so enchantment rules share all item enforcement. */
    public ItemRestriction asItemRestriction() {
        return new ItemRestriction(requires, List.of(new ItemMatcher.Enchanted(enchantments, checkBooks)), List.of(), actions,
                false, Optional.empty(), ItemRestriction.TooltipMode.APPEND, message);
    }
}
