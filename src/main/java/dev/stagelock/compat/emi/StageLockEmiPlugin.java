package dev.stagelock.compat.emi;

import dev.emi.emi.api.EmiEntrypoint;
import dev.emi.emi.api.EmiPlugin;
import dev.emi.emi.api.EmiRegistry;
import dev.stagelock.StageLock;
import dev.stagelock.client.viewer.ViewerRefresher;
import dev.stagelock.client.viewer.ViewerState;
import net.minecraft.world.item.ItemStack;

import java.lang.reflect.Method;

/**
 * EMI has no runtime hide/unhide API, so stacks and recipes are filtered while EMI loads plugins, and a
 * stage change asks EMI to reload.
 */
@EmiEntrypoint
public final class StageLockEmiPlugin implements EmiPlugin {

    private static boolean listening;
    private static Method reload;

    @Override
    public void register(EmiRegistry registry) {
        if (!listening) {
            listening = true;
            ViewerRefresher.addListener(StageLockEmiPlugin::reloadEmi);
        }
        // EMI is rebuilding from scratch with the live state; record that so no extra reload follows.
        ViewerRefresher.markApplied();
        if (!ViewerState.active() || !dev.stagelock.core.restriction.RestrictionManager.client().hidesAnythingInViewer()) return;
        registry.removeEmiStacks(stack -> {
            if (stack.getKey() instanceof net.minecraft.world.level.material.Fluid fluid) return ViewerState.isFluidHidden(fluid);
            ItemStack item = stack.getItemStack();
            return !item.isEmpty() && ViewerState.isItemHidden(item);
        });
        registry.removeRecipes(recipe -> recipe.getBackingRecipe() != null
                ? ViewerState.isRecipeHidden(recipe.getBackingRecipe())
                : recipe.getId() != null && ViewerState.isRecipeHidden(recipe.getId()));
    }

    /** {@code EmiReloadManager} is internal to EMI, so it is called reflectively and failures are only logged. */
    private static void reloadEmi() {
        try {
            if (reload == null) reload = Class.forName("dev.emi.emi.runtime.EmiReloadManager").getMethod("reloadRecipes");
            reload.invoke(null);
        } catch (ReflectiveOperationException | RuntimeException e) {
            StageLock.LOGGER.warn("Could not ask EMI to reload; hidden items update on the next EMI reload", e);
        }
    }
}
