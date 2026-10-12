package dev.stagelock.compat.rei;

import dev.architectury.event.EventResult;
import dev.stagelock.client.viewer.ViewerRefresher;
import dev.stagelock.client.viewer.ViewerState;
import me.shedaniel.rei.api.client.entry.filtering.base.BasicFilteringRule;
import me.shedaniel.rei.api.client.plugins.REIClientPlugin;
import me.shedaniel.rei.api.client.registry.display.DisplayRegistry;
import me.shedaniel.rei.api.common.entry.EntryStack;
import me.shedaniel.rei.api.common.util.EntryStacks;
import me.shedaniel.rei.forge.REIPluginClient;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Hides locked items through a REI filtering rule (re-evaluated via {@code markDirty}) and locked
 * recipes through a display visibility predicate (evaluated live by REI).
 */
@REIPluginClient
public final class StageLockReiPlugin implements REIClientPlugin {

    private static BasicFilteringRule.MarkDirty dirty;
    private static boolean listening;

    @Override
    public void registerBasicEntryFiltering(BasicFilteringRule<?> rule) {
        dirty = rule.hide(StageLockReiPlugin::hiddenStacks);
        if (!listening) {
            listening = true;
            ViewerRefresher.addListener(() -> {
                if (dirty != null) dirty.markDirty();
            });
        }
    }

    @Override
    public void registerDisplays(DisplayRegistry registry) {
        registry.registerVisibilityPredicate((category, display) -> display.getDisplayLocation()
                .filter(ViewerState::isRecipeHidden)
                .map(id -> EventResult.interruptFalse())
                .orElse(EventResult.pass()));
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
