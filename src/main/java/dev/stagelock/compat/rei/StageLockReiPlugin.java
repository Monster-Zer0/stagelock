package dev.stagelock.compat.rei;

import dev.architectury.event.EventResult;
import dev.stagelock.client.viewer.SlotRule;
import dev.stagelock.client.viewer.ViewerCategories;
import dev.stagelock.client.viewer.ViewerRefresher;
import dev.stagelock.client.viewer.ViewerState;
import dev.stagelock.core.config.ServerConfig;
import me.shedaniel.rei.api.client.entry.filtering.base.BasicFilteringRule;
import me.shedaniel.rei.api.client.plugins.REIClientPlugin;
import me.shedaniel.rei.api.client.registry.category.CategoryRegistry;
import me.shedaniel.rei.api.client.registry.display.DisplayRegistry;
import me.shedaniel.rei.api.common.category.CategoryIdentifier;
import me.shedaniel.rei.api.common.display.Display;
import me.shedaniel.rei.api.common.entry.EntryIngredient;
import me.shedaniel.rei.api.common.entry.EntryStack;
import me.shedaniel.rei.api.common.util.EntryStacks;
import me.shedaniel.rei.forge.REIPluginClient;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Hides locked items through a REI filtering rule (re-evaluated via {@code markDirty}), and locked
 * recipes, displays that show a hidden entry (see {@link SlotRule}) and categories through visibility
 * predicates (evaluated live by REI).
 */
@REIPluginClient
public final class StageLockReiPlugin implements REIClientPlugin {

    private static BasicFilteringRule.MarkDirty dirty;
    private static boolean listening;
    private static final Map<Display, Boolean> DISPLAYS = Collections.synchronizedMap(new WeakHashMap<>());

    @Override
    public void registerBasicEntryFiltering(BasicFilteringRule<?> rule) {
        dirty = rule.hide(StageLockReiPlugin::hiddenStacks);
        if (!listening) {
            listening = true;
            ViewerRefresher.addListener(() -> {
                DISPLAYS.clear();
                if (dirty != null) dirty.markDirty();
            });
        }
    }

    @Override
    public void registerCategories(CategoryRegistry registry) {
        ViewerCategories.register("REI", () -> CategoryRegistry.getInstance().stream()
                .map(config -> config.getCategoryIdentifier().getIdentifier()).toList(),
                id -> (int) DisplayRegistry.getInstance().get(CategoryIdentifier.of(id)).stream().filter(StageLockReiPlugin::isDisplayHidden).count());
        registry.registerVisibilityPredicate(category -> isCategoryHidden(category.getCategoryIdentifier())
                ? EventResult.interruptFalse()
                : EventResult.pass());
    }

    @Override
    public void registerDisplays(DisplayRegistry registry) {
        registry.registerVisibilityPredicate((category, display) -> isDisplayHidden(display)
                ? EventResult.interruptFalse()
                : EventResult.pass());
    }

    /** Hidden by a {@code viewer_category} rule, or every workstation of the category is hidden. */
    private static boolean isCategoryHidden(CategoryIdentifier<?> id) {
        if (!ViewerState.hidesEntries()) return false;
        if (ViewerState.isCategoryHidden(id.getIdentifier())) return true;
        if (ViewerState.recipeHiding() == ServerConfig.RecipeHiding.OUTPUT) return false;
        List<EntryIngredient> stations = CategoryRegistry.getInstance().tryGet(id).map(c -> c.getWorkstations()).orElse(List.of());
        if (stations.isEmpty()) return false;
        for (EntryIngredient station : stations) {
            for (EntryStack<?> stack : station) if (!isHidden(stack)) return false;
        }
        return true;
    }

    /** REI asks again and again while browsing, so answers are kept until the hidden set changes. */
    private static boolean isDisplayHidden(Display display) {
        if (!ViewerState.hidesEntries()) return false;
        return DISPLAYS.computeIfAbsent(display, d -> {
            if (d.getDisplayLocation().filter(ViewerState::isRecipeHidden).isPresent()) return true;
            List<EntryIngredient> slots = new ArrayList<>(d.getInputEntries());
            slots.addAll(d.getOutputEntries());
            return SlotRule.hides(ViewerState.recipeHiding(), slots, StageLockReiPlugin::isHidden);
        });
    }

    private static boolean isHidden(EntryStack<?> stack) {
        return switch (stack.getValue()) {
            case ItemStack item -> !item.isEmpty() && ViewerState.isItemHidden(item);
            case dev.architectury.fluid.FluidStack fluid -> !fluid.isEmpty() && ViewerState.isFluidHidden(fluid.getFluid());
            case null, default -> false;
        };
    }

    private static Collection<EntryStack<?>> hiddenStacks() {
        if (!ViewerState.active()) return List.of();
        List<EntryStack<?>> hidden = new ArrayList<>();
        for (Item item : BuiltInRegistries.ITEM) {
            ItemStack stack = new ItemStack(item);
            if (!stack.isEmpty() && ViewerState.isItemHidden(stack)) hidden.add(EntryStacks.of(stack));
        }
        for (var fluid : BuiltInRegistries.FLUID) {
            if (fluid.isSource(fluid.defaultFluidState()) && ViewerState.isFluidHidden(fluid)) hidden.add(EntryStacks.of(fluid));
        }
        return hidden;
    }
}
