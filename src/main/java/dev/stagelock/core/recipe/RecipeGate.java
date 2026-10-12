package dev.stagelock.core.recipe;

import dev.stagelock.api.StageSet;
import dev.stagelock.api.restriction.ItemAction;
import dev.stagelock.api.restriction.RecipeRestriction;
import dev.stagelock.api.restriction.RestrictionTypes;
import dev.stagelock.core.restriction.RestrictionManager;
import net.minecraft.core.HolderLookup;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeInput;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.WeakHashMap;

/**
 * Decides whether a recipe is locked for a stage set. A recipe is locked by a {@code stagelock:recipe}
 * rule that matches it, or by an item rule that blocks {@code craft} for its output.
 * <p>
 * Results are cached per stage-set snapshot, so repeated lookups (furnaces tick every tick) are a map hit.
 */
public final class RecipeGate {

    private static final Map<RestrictionManager, Cache> CACHES = new WeakHashMap<>();

    private record Cache(int generation, Map<StageSet, Map<ResourceLocation, Boolean>> results) {
    }

    private RecipeGate() {
    }

    public static boolean isLocked(RecipeHolder<?> recipe, StageSet stages, Level level) {
        return isLocked(level.isClientSide() ? RestrictionManager.client() : RestrictionManager.server(), recipe, stages, level.registryAccess());
    }

    public static synchronized boolean isLocked(RestrictionManager manager, RecipeHolder<?> recipe, StageSet stages, HolderLookup.Provider registries) {
        Cache cache = CACHES.get(manager);
        if (cache == null || cache.generation != manager.generation()) {
            cache = new Cache(manager.generation(), new WeakHashMap<>());
            CACHES.put(manager, cache);
        }
        return cache.results.computeIfAbsent(stages, s -> new HashMap<>())
                .computeIfAbsent(recipe.id(), id -> compute(manager, recipe, stages, registries));
    }

    private static boolean compute(RestrictionManager manager, RecipeHolder<?> recipe, StageSet stages, HolderLookup.Provider registries) {
        for (RecipeRestriction rule : manager.rules(RestrictionTypes.RECIPE).values()) {
            if (!rule.requires().test(stages) && rule.matches(recipe, registries)) return true;
        }
        ItemStack result = recipe.value().getResultItem(registries);
        return !result.isEmpty() && manager.items().find(result, stages, ItemAction.CRAFT) != null;
    }

    /** Whether recipe viewers should hide the recipe: a locked recipe rule with {@code viewer: hide}, or a hidden output. */
    public static boolean isHiddenInViewer(RestrictionManager manager, RecipeHolder<?> recipe, StageSet stages, HolderLookup.Provider registries) {
        for (RecipeRestriction rule : manager.rules(RestrictionTypes.RECIPE).values()) {
            if (rule.viewer() == dev.stagelock.api.restriction.ItemRestriction.ViewerMode.HIDE
                    && !rule.requires().test(stages) && rule.matches(recipe, registries)) return true;
        }
        ItemStack result = recipe.value().getResultItem(registries);
        return !result.isEmpty() && manager.items().find(result, stages, ItemAction.VIEWER) != null;
    }

    /** Vanilla's {@code getRecipeFor} logic, skipping locked recipes. */
    public static <I extends RecipeInput, T extends Recipe<I>> Optional<RecipeHolder<T>> find(
            Collection<RecipeHolder<T>> candidates, @Nullable RecipeHolder<T> last, I input, Level level, StageSet stages) {
        if (last != null && last.value().matches(input, level) && !isLocked(last, stages, level)) return Optional.of(last);
        for (RecipeHolder<T> holder : candidates) {
            if (holder.value().matches(input, level) && !isLocked(holder, stages, level)) return Optional.of(holder);
        }
        return Optional.empty();
    }

    /** Filters an already-resolved lookup (for callers with their own caches, like the crafter). */
    public static <T extends Recipe<?>> Optional<RecipeHolder<T>> filter(Optional<RecipeHolder<T>> found, @Nullable StageSet stages, Level level) {
        if (stages == null || found.isEmpty()) return found;
        return isLocked(found.get(), stages, level) ? Optional.empty() : found;
    }
}
