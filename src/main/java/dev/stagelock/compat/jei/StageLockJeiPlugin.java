package dev.stagelock.compat.jei;

import dev.stagelock.StageLock;
import dev.stagelock.client.viewer.SlotRule;
import dev.stagelock.client.viewer.ViewerCategories;
import dev.stagelock.client.viewer.ViewerRefresher;
import dev.stagelock.client.viewer.ViewerState;
import dev.stagelock.core.config.ServerConfig;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.neoforge.NeoForgeTypes;
import mezz.jei.api.gui.ingredient.IRecipeSlotView;
import mezz.jei.api.ingredients.IIngredientSupplier;
import mezz.jei.api.ingredients.ITypedIngredient;
import mezz.jei.api.recipe.IFocus;
import mezz.jei.api.recipe.IFocusFactory;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.IRecipeManager;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.category.IRecipeCategory;
import mezz.jei.api.runtime.IIngredientManager;
import mezz.jei.api.runtime.IJeiRuntime;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.neoforged.neoforge.fluids.FluidStack;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Hides locked items, recipes and categories in JEI, applying only the difference on every refresh.
 * Any entry that shows a hidden item or fluid is hidden too (see {@link SlotRule}), whichever mod made it.
 */
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
    private static final Set<RecipeType<?>> HIDDEN_CATEGORIES = new HashSet<>();

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
        HIDDEN_CATEGORIES.clear();
        ViewerCategories.register("JEI", () -> jeiRuntimeCategories(jeiRuntime), StageLockJeiPlugin::hiddenEntries);
        if (!listening) {
            listening = true;
            ViewerRefresher.addListener(StageLockJeiPlugin::refresh);
        }
        // JEI only finishes publishing its runtime after this call, and recipe layouts need it.
        Minecraft.getInstance().tell(() -> {
            if (runtime != jeiRuntime) return;
            refresh();
            ViewerRefresher.markApplied();
        });
    }

    @Override
    public void onRuntimeUnavailable() {
        runtime = null;
    }

    private static List<ResourceLocation> jeiRuntimeCategories(IJeiRuntime rt) {
        return rt.getRecipeManager().createRecipeCategoryLookup().includeHidden().get()
                .map(category -> category.getRecipeType().getUid()).toList();
    }

    private static int hiddenEntries(ResourceLocation category) {
        for (var entry : HIDDEN_RECIPES.entrySet()) {
            if (entry.getKey().getUid().equals(category)) return entry.getValue().size();
        }
        return 0;
    }

    private static void refresh() {
        IJeiRuntime rt = runtime;
        if (rt == null) return;
        boolean active = ViewerState.active();
        refreshItems(rt.getIngredientManager(), active);
        refreshFluids(rt.getIngredientManager(), active);
        refreshRecipes(rt, active);
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

    private static void refreshRecipes(IJeiRuntime rt, boolean active) {
        IRecipeManager recipes = rt.getRecipeManager();
        Map<RecipeType<?>, Set<Object>> byIngredient = active ? hiddenByIngredients(rt) : Map.of();
        Set<RecipeType<?>> categoriesToHide = new HashSet<>();
        for (IRecipeCategory<?> category : recipes.createRecipeCategoryLookup().includeHidden().get().toList()) {
            RecipeType<?> type = category.getRecipeType();
            refreshCategory(recipes, type, active, byIngredient.getOrDefault(type, Set.of()));
            if (active && ViewerState.isCategoryHidden(type.getUid())) categoriesToHide.add(type);
        }
        for (RecipeType<?> type : HIDDEN_CATEGORIES) if (!categoriesToHide.contains(type)) recipes.unhideRecipeCategory(type);
        for (RecipeType<?> type : categoriesToHide) if (!HIDDEN_CATEGORIES.contains(type)) recipes.hideRecipeCategory(type);
        HIDDEN_CATEGORIES.clear();
        HIDDEN_CATEGORIES.addAll(categoriesToHide);
    }

    /**
     * Entries of any category (JEI's own anvil and grindstone entries, other mods' loot and trade
     * categories, ...) that show a hidden item or fluid. JEI's ingredient index finds the candidates, so
     * only entries that mention something hidden have their slots checked.
     */
    private static Map<RecipeType<?>, Set<Object>> hiddenByIngredients(IJeiRuntime rt) {
        if (!ViewerState.hidesEntries() || ViewerState.recipeHiding() == ServerConfig.RecipeHiding.OUTPUT
                || (hiddenItems.isEmpty() && hiddenFluids.isEmpty())) return Map.of();
        IFocusFactory factory = rt.getJeiHelpers().getFocusFactory();
        List<IFocus<?>> focuses = new ArrayList<>();
        for (RecipeIngredientRole role : new RecipeIngredientRole[]{RecipeIngredientRole.INPUT, RecipeIngredientRole.OUTPUT}) {
            for (ItemStack stack : hiddenItems) focuses.add(factory.createFocus(role, VanillaTypes.ITEM_STACK, stack));
            for (FluidStack stack : hiddenFluids) focuses.add(factory.createFocus(role, NeoForgeTypes.FLUID_STACK, stack));
        }
        IRecipeManager recipes = rt.getRecipeManager();
        IFocusGroup noFocus = factory.getEmptyFocusGroup();
        ServerConfig.RecipeHiding mode = ViewerState.recipeHiding();
        Map<RecipeType<?>, Set<Object>> out = new HashMap<>();
        for (IRecipeCategory<?> category : recipes.createRecipeCategoryLookup().limitFocus(focuses).includeHidden().get().toList()) {
            Set<Object> hide = hiddenEntries(recipes, category, focuses, noFocus, mode);
            if (!hide.isEmpty()) out.put(category.getRecipeType(), hide);
        }
        return out;
    }

    private static <T> Set<Object> hiddenEntries(IRecipeManager recipes, IRecipeCategory<T> category, List<IFocus<?>> focuses,
                                                 IFocusGroup noFocus, ServerConfig.RecipeHiding mode) {
        Set<Object> hide = identitySet();
        recipes.createRecipeLookup(category.getRecipeType()).limitFocus(focuses).includeHidden().get().forEach(recipe -> {
            try {
                if (SlotRule.hides(mode, slots(recipes, category, recipe, noFocus, mode), ViewerState::isIngredientHidden)) hide.add(recipe);
            } catch (RuntimeException e) {
                StageLock.LOGGER.debug("Could not read the slots of a {} entry", category.getRecipeType().getUid(), e);
            }
        });
        return hide;
    }

    /**
     * The entry's slots. {@code STRICT} only needs to know whether anything hidden appears, which JEI's
     * flat ingredient list answers cheaply; the other modes need real slots, which only a layout has. If
     * the category can't build a layout, each role's ingredients count as one slot.
     */
    private static <T> List<List<Object>> slots(IRecipeManager recipes, IRecipeCategory<T> category, T recipe,
                                                         IFocusGroup noFocus, ServerConfig.RecipeHiding mode) {
        if (mode != ServerConfig.RecipeHiding.STRICT) {
            var layout = recipes.createRecipeLayoutDrawable(category, recipe, noFocus);
            if (layout.isPresent()) {
                List<List<Object>> slots = new ArrayList<>();
                for (IRecipeSlotView view : layout.get().getRecipeSlotsView().getSlotViews()) {
                    slots.add(view.getAllIngredientsList().stream().filter(Objects::nonNull).<Object>map(ITypedIngredient::getIngredient).toList());
                }
                return slots;
            }
        }
        List<List<Object>> slots = new ArrayList<>();
        IIngredientSupplier supplier = recipes.getRecipeIngredients(category, recipe);
        for (RecipeIngredientRole role : RecipeIngredientRole.values()) {
            slots.add(supplier.getIngredients(role).stream().filter(Objects::nonNull).<Object>map(ITypedIngredient::getIngredient).toList());
        }
        return slots;
    }

    private static <T> void refreshCategory(IRecipeManager recipes, RecipeType<T> type, boolean active, Set<Object> byIngredient) {
        Set<Object> previous = HIDDEN_RECIPES.getOrDefault(type, Set.of());
        Set<Object> hide = identitySet();
        hide.addAll(byIngredient);
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
