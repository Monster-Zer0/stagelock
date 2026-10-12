package dev.stagelock.client.viewer;

import dev.stagelock.api.StageLockAPI;
import dev.stagelock.api.restriction.ItemAction;
import dev.stagelock.core.enforce.Restrictions;
import dev.stagelock.core.recipe.RecipeGate;
import dev.stagelock.core.restriction.RestrictionManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;

/**
 * What recipe viewers (JEI, REI, EMI) should hide for the local player. Shared by all viewer
 * integrations; each plugin only translates these answers into its own API.
 */
public final class ViewerState {

    private ViewerState() {
    }

    /** False when nothing should be hidden (no player, or the player is exempt, e.g. creative). */
    public static boolean active() {
        LocalPlayer player = Minecraft.getInstance().player;
        return player != null && !Restrictions.isExempt(player);
    }

    public static boolean isItemHidden(ItemStack stack) {
        if (!RestrictionManager.client().hidesAnythingInViewer()) return false;
        LocalPlayer player = Minecraft.getInstance().player;
        return player != null && Restrictions.item(player, stack, ItemAction.VIEWER) != null;
    }

    public static boolean isFluidHidden(net.minecraft.world.level.material.Fluid fluid) {
        if (!RestrictionManager.client().hidesAnythingInViewer()) return false;
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null || Restrictions.isExempt(player)) return false;
        var rule = dev.stagelock.core.enforce.WorldRestrictions.fluid(player, fluid,
                a -> a.viewer() == dev.stagelock.api.restriction.ItemRestriction.ViewerMode.HIDE);
        return rule != null;
    }

    public static boolean isRecipeHidden(RecipeHolder<?> recipe) {
        if (!RestrictionManager.client().hidesAnythingInViewer()) return false;
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null || Restrictions.isExempt(player)) return false;
        return RecipeGate.isHiddenInViewer(RestrictionManager.client(), recipe, StageLockAPI.getStages(player), player.level().registryAccess());
    }

    public static boolean isRecipeHidden(ResourceLocation id) {
        if (!RestrictionManager.client().hidesAnythingInViewer()) return false;
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) return false;
        return player.level().getRecipeManager().byKey(id).map(ViewerState::isRecipeHidden).orElse(false);
    }
}
