package dev.stagelock.core.restriction;

import dev.stagelock.api.StageRequirement;
import dev.stagelock.api.restriction.EnchantmentRestriction;
import dev.stagelock.api.restriction.FluidRestriction;
import dev.stagelock.api.restriction.ItemRestriction;
import dev.stagelock.api.restriction.RecipeRestriction;
import dev.stagelock.api.restriction.Restriction;
import dev.stagelock.api.restriction.RestrictionType;
import dev.stagelock.api.restriction.RestrictionTypes;
import dev.stagelock.api.event.RestrictionsReloadedEvent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.common.NeoForge;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Holds the active restriction rules and their compiled lookup indexes. There is one instance per
 * logical side: the server's comes from datapacks, the client's is a copy synced by the server.
 * <p>
 * Indexes are compiled lazily, after rules or tags change, so tag/mod selectors are expanded against
 * the current registries.
 */
public final class RestrictionManager {

    private static final RestrictionManager SERVER = new RestrictionManager();
    private static final RestrictionManager CLIENT = new RestrictionManager();

    private Map<ResourceLocation, Restriction> rules = Map.of();
    @Nullable
    private ItemIndex items;
    @Nullable
    private Map<ResourceLocation, StageRequirement> viewerHiding;
    private int generation;

    public static RestrictionManager server() {
        return SERVER;
    }

    public static RestrictionManager client() {
        return CLIENT;
    }

    public static RestrictionManager of(Player player) {
        return player.level().isClientSide() ? CLIENT : SERVER;
    }

    public void setRules(Map<ResourceLocation, Restriction> rules) {
        synchronized (this) {
            this.rules = java.util.Collections.unmodifiableMap(new LinkedHashMap<>(rules));
            invalidate();
        }
        NeoForge.EVENT_BUS.post(new RestrictionsReloadedEvent(this == CLIENT));
    }

    /** Recompile indexes on next access; call when tags or registries change. */
    public synchronized void invalidate() {
        items = null;
        viewerHiding = null;
        generation++;
    }

    public Map<ResourceLocation, Restriction> rules() {
        return rules;
    }

    public <T extends Restriction> Map<ResourceLocation, T> rules(RestrictionType<T> type) {
        Map<ResourceLocation, T> out = new LinkedHashMap<>();
        rules.forEach((id, rule) -> {
            if (rule.type() == type) {
                @SuppressWarnings("unchecked") T cast = (T) rule;
                out.put(id, cast);
            }
        });
        return out;
    }

    /** Changes whenever rules or indexes are rebuilt; lets callers cache derived results. */
    public int generation() {
        return generation;
    }

    public ItemIndex items() {
        ItemIndex index = items;
        if (index != null) return index;
        synchronized (this) {
            if (items == null) {
                Map<ResourceLocation, ItemRestriction> itemRules = new LinkedHashMap<>(rules(RestrictionTypes.ITEM));
                rules(RestrictionTypes.ENCHANTMENT).forEach((id, rule) -> itemRules.put(id, rule.asItemRestriction()));
                items = ItemIndex.compile(itemRules);
            }
            return items;
        }
    }

    /**
     * Rules that hide something in recipe viewers (item, enchantment and fluid rules with
     * {@code viewer: hide}, recipe rules with {@code viewer: hide}, viewer category rules), mapped to their requirement.
     */
    public synchronized Map<ResourceLocation, StageRequirement> viewerHidingRules() {
        if (viewerHiding == null) {
            Map<ResourceLocation, StageRequirement> out = new LinkedHashMap<>();
            rules.forEach((id, rule) -> {
                ItemRestriction.ViewerMode mode = switch (rule) {
                    case ItemRestriction item -> item.actions().viewer();
                    case EnchantmentRestriction enchantment -> enchantment.actions().viewer();
                    case FluidRestriction fluid -> fluid.actions().viewer();
                    case RecipeRestriction recipe -> recipe.viewer();
                    case dev.stagelock.api.restriction.ViewerCategoryRestriction category -> ItemRestriction.ViewerMode.HIDE;
                    default -> ItemRestriction.ViewerMode.SHOW;
                };
                if (mode == ItemRestriction.ViewerMode.HIDE) out.put(id, rule.requires());
            });
            viewerHiding = java.util.Collections.unmodifiableMap(out);
        }
        return viewerHiding;
    }

    /** Cheap check: whether any rule can hide anything in recipe viewers at all. */
    public boolean hidesAnythingInViewer() {
        return !viewerHidingRules().isEmpty();
    }

    public record Entry<T extends Restriction>(ResourceLocation id, T rule) {
    }
}
