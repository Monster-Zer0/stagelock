package dev.stagelock.compat.jei;

import dev.stagelock.StageLock;
import dev.stagelock.client.viewer.ViewerRefresher;
import dev.stagelock.client.viewer.ViewerState;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.neoforge.NeoForgeTypes;
import mezz.jei.api.recipe.IRecipeManager;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.category.IRecipeCategory;
import mezz.jei.api.runtime.IIngredientManager;
import mezz.jei.api.runtime.IJeiRuntime;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.neoforged.neoforge.fluids.FluidStack;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Hides locked items and recipes in JEI, applying only the difference on every refresh. */
@JeiPlugin
public final class StageLockJeiPlugin implements IModPlugin {

    private static final ResourceLocation UID = StageLock.id("jei");
    private static boolean listening;

    @Nullable
    private static IJeiRuntime runtime;
    private static List<ItemStack> allItems = List.of();
    private static Set<ItemStack> hiddenItems = identitySet();
    private static List<FluidStack> allFluids = List.of();
    private static Set<FluidStack> hiddenFluids = identitySet();
    private static final Map<RecipeType<?>, Set<Object>> HIDDEN_RECIPES = new HashMap<>();

    @Override
    public ResourceLocation getPluginUid() {
        return UID;
    }

    @Override
    public void onRuntimeAvailable(IJeiRuntime jeiRuntime) {
        runtime = jeiRuntime;
        allItems = List.copyOf(jeiRuntime.getIngredientManager().getAllItemStacks());
        hiddenItems = identitySet();
        allFluids = List.copyOf(jeiRuntime.getIngredientManager().getAllIngredients(NeoForgeTypes.FLUID_STACK));
        hiddenFluids = identitySet();
        HIDDEN_RECIPES.clear();
        if (!listening) {
            listening = true;
            ViewerRefresher.addListener(StageLockJeiPlugin::refresh);
        }
        refresh();
        ViewerRefresher.markApplied();
    }

    @Override
    public void onRuntimeUnavailable() {
        runtime = null;
    }

    private static void refresh() {
        IJeiRuntime rt = runtime;
        if (rt == null) return;
        boolean active = ViewerState.active();
        refreshItems(rt.getIngredientManager(), active);
        refreshFluids(rt.getIngredientManager(), active);
        refreshRecipes(rt.getRecipeManager(), active);
    }

    private static void refreshItems(IIngredientManager ingredients, boolean active) {
        Set<ItemStack> hide = identitySet();
        if (active) {
            for (ItemStack stack : allItems) {
                if (ViewerState.isItemHidden(stack)) hide.add(stack);
            }
        }
        List<ItemStack> toShow = new ArrayList<>();
        for (ItemStack stack : hiddenItems) if (!hide.contains(stack)) toShow.add(stack);
        List<ItemStack> toHide = new ArrayList<>();
        for (ItemStack stack : hide) if (!hiddenItems.contains(stack)) toHide.add(stack);
        if (!toShow.isEmpty()) ingredients.addIngredientsAtRuntime(VanillaTypes.ITEM_STACK, toShow);
        if (!toHide.isEmpty()) ingredients.removeIngredientsAtRuntime(VanillaTypes.ITEM_STACK, toHide);
        hiddenItems = hide;
    }

    private static void refreshFluids(IIngredientManager ingredients, boolean active) {
        Set<FluidStack> hide = identitySet();
        if (active) {
            for (FluidStack stack : allFluids) {
                if (ViewerState.isFluidHidden(stack.getFluid())) hide.add(stack);
            }
        }
        List<FluidStack> toShow = new ArrayList<>();
        for (FluidStack stack : hiddenFluids) if (!hide.contains(stack)) toShow.add(stack);
        List<FluidStack> toHide = new ArrayList<>();
        for (FluidStack stack : hide) if (!hiddenFluids.contains(stack)) toHide.add(stack);
        if (!toShow.isEmpty()) ingredients.addIngredientsAtRuntime(NeoForgeTypes.FLUID_STACK, toShow);
        if (!toHide.isEmpty()) ingredients.removeIngredientsAtRuntime(NeoForgeTypes.FLUID_STACK, toHide);
        hiddenFluids = hide;
    }

    private static void refreshRecipes(IRecipeManager recipes, boolean active) {
        for (IRecipeCategory<?> category : recipes.createRecipeCategoryLookup().includeHidden().get().toList()) {
            refreshCategory(recipes, category.getRecipeType(), active);
        }
    }

    private static <T> void refreshCategory(IRecipeManager recipes, RecipeType<T> type, boolean active) {
        Set<Object> previous = HIDDEN_RECIPES.getOrDefault(type, Set.of());
        Set<Object> hide = identitySet();
        if (active) {
            recipes.createRecipeLookup(type).includeHidden().get().forEach(recipe -> {
                if (recipe instanceof RecipeHolder<?> holder && ViewerState.isRecipeHidden(holder)) hide.add(recipe);
            });
        }
        List<T> toShow = new ArrayList<>();
        List<T> toHide = new ArrayList<>();
        for (Object recipe : previous) if (!hide.contains(recipe)) toShow.add(cast(recipe));
        for (Object recipe : hide) if (!previous.contains(recipe)) toHide.add(cast(recipe));
        if (!toShow.isEmpty()) recipes.unhideRecipes(type, toShow);
        if (!toHide.isEmpty()) recipes.hideRecipes(type, toHide);
        if (hide.isEmpty()) HIDDEN_RECIPES.remove(type);
        else HIDDEN_RECIPES.put(type, hide);
    }

    @SuppressWarnings("unchecked")
    private static <T> T cast(Object recipe) {
        return (T) recipe;
    }

    private static <T> Set<T> identitySet() {
        return Collections.newSetFromMap(new IdentityHashMap<>());
    }
}
