package dev.stagelock.client.viewer;

import dev.stagelock.api.StageLockAPI;
import dev.stagelock.api.restriction.ItemAction;
import dev.stagelock.api.restriction.RestrictionTypes;
import dev.stagelock.api.restriction.ViewerCategoryRestriction;
import dev.stagelock.core.config.ServerConfig;
import dev.stagelock.core.enforce.Restrictions;
import dev.stagelock.core.recipe.RecipeGate;
import dev.stagelock.core.restriction.RestrictionManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.fluids.FluidStack;

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

    public static boolean isFluidHidden(Fluid fluid) {
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

    /** Which viewer entries a hidden item or fluid takes down with it (server config {@code viewers.recipeHiding}). */
    public static ServerConfig.RecipeHiding recipeHiding() {
        return ServerConfig.recipeHiding();
    }

    /** Whether viewer entries should be checked for hidden ingredients at all right now. */
    public static boolean hidesEntries() {
        return active() && RestrictionManager.client().hidesAnythingInViewer();
    }

    /**
     * Whether a viewer ingredient is hidden: an {@link ItemStack}, a NeoForge {@link FluidStack} or a
     * {@link Fluid}. Anything else (other ingredient types) is never hidden.
     */
    public static boolean isIngredientHidden(Object ingredient) {
        return switch (ingredient) {
            case ItemStack stack -> !stack.isEmpty() && isItemHidden(stack);
            case FluidStack stack -> !stack.isEmpty() && isFluidHidden(stack.getFluid());
            case Fluid fluid -> isFluidHidden(fluid);
            default -> false;
        };
    }

    /** Whether a whole viewer category (JEI recipe type, REI category or EMI category id) is hidden by a {@code viewer_category} rule. */
    public static boolean isCategoryHidden(ResourceLocation category) {
        if (!RestrictionManager.client().hidesAnythingInViewer()) return false;
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null || Restrictions.isExempt(player)) return false;
        var stages = StageLockAPI.getStages(player);
        for (ViewerCategoryRestriction rule : RestrictionManager.client().rules(RestrictionTypes.VIEWER_CATEGORY).values()) {
            if (rule.categories().contains(category) && !rule.requires().test(stages)) return true;
        }
        return false;
    }

    public static boolean isRecipeHidden(ResourceLocation id) {
        if (!RestrictionManager.client().hidesAnythingInViewer()) return false;
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) return false;
        return player.level().getRecipeManager().byKey(id).map(ViewerState::isRecipeHidden).orElse(false);
    }
}
