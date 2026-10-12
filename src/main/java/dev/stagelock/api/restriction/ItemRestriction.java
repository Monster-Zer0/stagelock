package dev.stagelock.api.restriction;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.stagelock.api.StageRequirement;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * {@code "type": "stagelock:item"} - blocks interactions with matching items until the requirement is met.
 *
 * <pre>{@code
 * {
 *   "type": "stagelock:item",
 *   "requires": "mypack:iron_age",
 *   "items": ["minecraft:diamond", "#c:gems/diamond", "@mekanism"],
 *   "except": ["mekanism:configurator"],
 *   "actions": { "pickup": true, "hold": "drop", "viewer": "hide" },
 *   "hidden_name": "Unfamiliar Item",
 *   "tooltip": "replace"
 * }
 * }</pre>
 */
public record ItemRestriction(
        StageRequirement requires,
        List<ItemMatcher> items,
        List<ItemMatcher> except,
        Actions actions,
        boolean hideName,
        Optional<Component> hiddenName,
        TooltipMode tooltip,
        Optional<Component> message
) implements Restriction {

    public static final MapCodec<ItemRestriction> MAP_CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            StageRequirement.CODEC.fieldOf("requires").forGetter(ItemRestriction::requires),
            ItemMatcher.LIST_CODEC.fieldOf("items").forGetter(ItemRestriction::items),
            ItemMatcher.LIST_CODEC.optionalFieldOf("except", List.of()).forGetter(ItemRestriction::except),
            Actions.CODEC.optionalFieldOf("actions", Actions.ALL).forGetter(ItemRestriction::actions),
            Codec.BOOL.optionalFieldOf("hide_name", true).forGetter(ItemRestriction::hideName),
            ComponentSerialization.CODEC.optionalFieldOf("hidden_name").forGetter(ItemRestriction::hiddenName),
            TooltipMode.CODEC.optionalFieldOf("tooltip", TooltipMode.REPLACE).forGetter(ItemRestriction::tooltip),
            ComponentSerialization.CODEC.optionalFieldOf("message").forGetter(ItemRestriction::message)
    ).apply(i, ItemRestriction::new));

    public static final Component DEFAULT_HIDDEN_NAME = Component.translatable("item.stagelock.unfamiliar");

    @Override
    public RestrictionType<?> type() {
        return RestrictionTypes.ITEM;
    }

    /** Whether the stack matches this rule's item selectors (ignores the requirement). */
    public boolean matches(ItemStack stack) {
        boolean included = false;
        for (ItemMatcher matcher : items) {
            if (matcher.matches(stack)) {
                included = true;
                break;
            }
        }
        if (!included) return false;
        for (ItemMatcher matcher : except) {
            if (matcher.matches(stack)) return false;
        }
        return true;
    }

    public boolean blocks(ItemAction action) {
        return actions.blocks(action);
    }

    public Component displayName() {
        return hiddenName.orElse(DEFAULT_HIDDEN_NAME);
    }

    public enum HoldMode implements StringRepresentable {
        ALLOW, DROP, DESTROY;

        public static final Codec<HoldMode> CODEC = StringRepresentable.fromEnum(HoldMode::values);

        @Override
        public String getSerializedName() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    public enum ViewerMode implements StringRepresentable {
        SHOW, HIDE,
        /** Shown in recipe viewers with a padlock icon (same as SHOW; the icon is drawn for every locked item). */
        LOCK;

        public static final Codec<ViewerMode> CODEC = StringRepresentable.fromEnum(ViewerMode::values);

        @Override
        public String getSerializedName() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    public enum TooltipMode implements StringRepresentable {
        /** Keep only the (hidden) name and the requirement lines. */
        REPLACE,
        /** Keep the original tooltip and append the requirement lines. */
        APPEND,
        /** Leave the tooltip alone. */
        NONE;

        public static final Codec<TooltipMode> CODEC = StringRepresentable.fromEnum(TooltipMode::values);

        @Override
        public String getSerializedName() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    /** Which interactions the rule blocks. Every field defaults to blocking. */
    public record Actions(boolean pickup, boolean use, boolean place, boolean attack, boolean mine,
                          boolean equip, HoldMode hold, boolean craft, ViewerMode viewer) {

        public static final Actions ALL = new Actions(true, true, true, true, true, true, HoldMode.DROP, true, ViewerMode.HIDE);

        public static final Codec<Actions> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.BOOL.optionalFieldOf("pickup", true).forGetter(Actions::pickup),
                Codec.BOOL.optionalFieldOf("use", true).forGetter(Actions::use),
                Codec.BOOL.optionalFieldOf("place", true).forGetter(Actions::place),
                Codec.BOOL.optionalFieldOf("attack", true).forGetter(Actions::attack),
                Codec.BOOL.optionalFieldOf("mine", true).forGetter(Actions::mine),
                Codec.BOOL.optionalFieldOf("equip", true).forGetter(Actions::equip),
                HoldMode.CODEC.optionalFieldOf("hold", HoldMode.DROP).forGetter(Actions::hold),
                Codec.BOOL.optionalFieldOf("craft", true).forGetter(Actions::craft),
                ViewerMode.CODEC.optionalFieldOf("viewer", ViewerMode.HIDE).forGetter(Actions::viewer)
        ).apply(i, Actions::new));

        public boolean blocks(ItemAction action) {
            return switch (action) {
                // An item you may not hold must not be picked up either, or it would bounce forever.
                case PICKUP -> pickup || hold != HoldMode.ALLOW;
                case USE -> use;
                case PLACE -> place;
                case ATTACK -> attack;
                case MINE -> mine;
                case EQUIP -> equip;
                case HOLD -> hold != HoldMode.ALLOW;
                case CRAFT -> craft;
                case VIEWER -> viewer == ViewerMode.HIDE;
            };
        }
    }
}
