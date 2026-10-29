package dev.stagelock.compat.emi;

import dev.emi.emi.api.EmiEntrypoint;
import dev.emi.emi.api.EmiPlugin;
import dev.emi.emi.api.EmiApi;
import dev.emi.emi.api.EmiRegistry;
import dev.emi.emi.api.recipe.EmiRecipe;
import dev.emi.emi.api.recipe.EmiRecipeCategory;
import dev.emi.emi.api.stack.EmiIngredient;
import dev.emi.emi.api.stack.EmiStack;
import dev.stagelock.StageLock;
import dev.stagelock.client.viewer.SlotRule;
import dev.stagelock.client.viewer.ViewerCategories;
import dev.stagelock.client.viewer.ViewerRefresher;
import dev.stagelock.client.viewer.ViewerState;
import dev.stagelock.core.config.ServerConfig;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.material.Fluid;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * EMI has no runtime hide/unhide API, so stacks and recipes are filtered while EMI loads plugins, and a
 * stage change asks EMI to reload. Any recipe that shows a hidden stack is filtered too (see {@link SlotRule}),
 * as are categories whose workstations are all hidden.
 */
@EmiEntrypoint
public final class StageLockEmiPlugin implements EmiPlugin {

    private static boolean listening;
    private static Method reload;
    private static Field workstations;
    /** Recipes removed per category in the last bake, for {@code /stagelock_categories}. */
    private static final Map<ResourceLocation, Integer> REMOVED = new java.util.concurrent.ConcurrentHashMap<>();
    private static boolean workstationsWarned;

    @Override
    public void register(EmiRegistry registry) {
        if (!listening) {
            listening = true;
            ViewerRefresher.addListener(StageLockEmiPlugin::reloadEmi);
        }
        // EMI is rebuilding from scratch with the live state; record that so no extra reload follows.
        ViewerRefresher.markApplied();
        // The baked manager lists every registered category, including ones StageLock emptied.
        ViewerCategories.register("EMI", () -> EmiApi.getRecipeManager().getCategories().stream().map(EmiRecipeCategory::getId).toList(),
                id -> REMOVED.getOrDefault(id, 0));
        REMOVED.clear();
        if (!ViewerState.hidesEntries()) return;
        registry.removeEmiStacks(StageLockEmiPlugin::isHidden);
        ServerConfig.RecipeHiding mode = ViewerState.recipeHiding();
        // Recipe filters run while EMI bakes, after every plugin has registered its workstations.
        Map<EmiRecipeCategory, Boolean> hiddenCategories = new HashMap<>();
        registry.removeRecipes(recipe -> {
            if (!isRecipeHidden(recipe, mode, hiddenCategories)) return false;
            if (recipe.getCategory() != null) REMOVED.merge(recipe.getCategory().getId(), 1, Integer::sum);
            return true;
        });
    }

    private static boolean isRecipeHidden(EmiRecipe recipe, ServerConfig.RecipeHiding mode, Map<EmiRecipeCategory, Boolean> hiddenCategories) {
        if (recipe.getBackingRecipe() != null ? ViewerState.isRecipeHidden(recipe.getBackingRecipe())
                : recipe.getId() != null && ViewerState.isRecipeHidden(recipe.getId())) return true;
        EmiRecipeCategory category = recipe.getCategory();
        if (category != null && hiddenCategories.computeIfAbsent(category, StageLockEmiPlugin::isCategoryHidden)) return true;
        return SlotRule.hides(mode, slots(recipe), StageLockEmiPlugin::isHidden);
    }

    private static boolean isHidden(EmiStack stack) {
        if (stack.getKey() instanceof Fluid fluid) return ViewerState.isFluidHidden(fluid);
        ItemStack item = stack.getItemStack();
        return !item.isEmpty() && ViewerState.isItemHidden(item);
    }

    private static List<List<EmiStack>> slots(EmiRecipe recipe) {
        List<List<EmiStack>> slots = new ArrayList<>();
        for (EmiIngredient input : recipe.getInputs()) slots.add(input.getEmiStacks());
        for (EmiIngredient catalyst : recipe.getCatalysts()) slots.add(catalyst.getEmiStacks());
        for (EmiStack output : recipe.getOutputs()) slots.add(List.of(output));
        return slots;
    }

    /** Hidden by a {@code viewer_category} rule, or every workstation of the category is hidden. */
    private static boolean isCategoryHidden(EmiRecipeCategory category) {
        if (ViewerState.isCategoryHidden(category.getId())) return true;
        if (ViewerState.recipeHiding() == ServerConfig.RecipeHiding.OUTPUT) return false;
        List<EmiIngredient> stations = workstations(category);
        if (stations.isEmpty()) return false;
        for (EmiIngredient station : stations) {
            for (EmiStack stack : station.getEmiStacks()) if (!isHidden(stack)) return false;
        }
        return true;
    }

    /** EMI's API only exposes workstations after baking, so the registered ones are read from its internals. */
    @SuppressWarnings("unchecked")
    private static List<EmiIngredient> workstations(EmiRecipeCategory category) {
        try {
            if (workstations == null) {
                workstations = Class.forName("dev.emi.emi.registry.EmiRecipes").getDeclaredField("workstations");
                workstations.setAccessible(true);
            }
            var map = (Map<EmiRecipeCategory, List<EmiIngredient>>) workstations.get(null);
            return map.getOrDefault(category, List.of());
        } catch (ReflectiveOperationException | RuntimeException e) {
            if (!workstationsWarned) {
                workstationsWarned = true;
                StageLock.LOGGER.warn("Could not read EMI workstations; categories are not hidden by their workstation", e);
            }
            return List.of();
        }
    }

    /**
     * {@code EmiReloadManager} is internal to EMI, so it is called reflectively and failures are only logged.
     * {@code reload()} rebuilds right away; {@code reloadRecipes()} only marks recipes as synced and waits
     * for a tag sync that a stage change never sends.
     */
    private static void reloadEmi() {
        try {
            if (reload == null) reload = Class.forName("dev.emi.emi.runtime.EmiReloadManager").getMethod("reload");
            reload.invoke(null);
        } catch (ReflectiveOperationException | RuntimeException e) {
            StageLock.LOGGER.warn("Could not ask EMI to reload; hidden items update on the next EMI reload", e);
        }
    }
}
