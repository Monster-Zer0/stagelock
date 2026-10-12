package dev.stagelock.api.restriction;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.stagelock.api.StageRequirement;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;

import java.util.List;

/**
 * {@code "type": "stagelock:recipe"} - locks recipes until the requirement is met. A recipe is locked
 * when it matches any of the selectors.
 *
 * <pre>{@code
 * {
 *   "type": "stagelock:recipe",
 *   "requires": "mypack:tech",
 *   "recipes": ["minecraft:iron_pickaxe", "@create"],
 *   "outputs": ["#c:ingots/steel"],
 *   "recipe_types": ["minecraft:smelting"]
 * }
 * }</pre>
 */
public record RecipeRestriction(
        StageRequirement requires,
        List<RecipeIdMatcher> recipes,
        List<ItemMatcher> outputs,
        List<ResourceLocation> recipeTypes,
        ItemRestriction.ViewerMode viewer
) implements Restriction {

    public static final MapCodec<RecipeRestriction> MAP_CODEC = RecordCodecBuilder.<RecipeRestriction>mapCodec(i -> i.group(
            StageRequirement.CODEC.fieldOf("requires").forGetter(RecipeRestriction::requires),
            RecipeIdMatcher.LIST_CODEC.optionalFieldOf("recipes", List.of()).forGetter(RecipeRestriction::recipes),
            ItemMatcher.LIST_CODEC.optionalFieldOf("outputs", List.of()).forGetter(RecipeRestriction::outputs),
            ResourceLocation.CODEC.listOf().optionalFieldOf("recipe_types", List.of()).forGetter(RecipeRestriction::recipeTypes),
            ItemRestriction.ViewerMode.CODEC.optionalFieldOf("viewer", ItemRestriction.ViewerMode.HIDE).forGetter(RecipeRestriction::viewer)
    ).apply(i, RecipeRestriction::new)).validate(rule -> rule.recipes.isEmpty() && rule.outputs.isEmpty() && rule.recipeTypes.isEmpty()
            ? DataResult.error(() -> "Recipe restriction needs at least one of recipes, outputs, recipe_types")
            : DataResult.success(rule));

    @Override
    public RestrictionType<?> type() {
        return RestrictionTypes.RECIPE;
    }

    public boolean matches(RecipeHolder<?> recipe, HolderLookup.Provider registries) {
        for (RecipeIdMatcher matcher : recipes) {
            if (matcher.matches(recipe.id())) return true;
        }
        if (!recipeTypes.isEmpty()) {
            ResourceLocation type = BuiltInRegistries.RECIPE_TYPE.getKey(recipe.value().getType());
            if (recipeTypes.contains(type)) return true;
        }
        if (!outputs.isEmpty()) {
            ItemStack result = recipe.value().getResultItem(registries);
            if (!result.isEmpty()) {
                for (ItemMatcher matcher : outputs) {
                    if (matcher.matches(result)) return true;
                }
            }
        }
        return false;
    }

    /** A recipe id ({@code minecraft:iron_pickaxe}) or every recipe of a namespace ({@code @create}). */
    public record RecipeIdMatcher(String namespace, ResourceLocation id) {

        public static final Codec<RecipeIdMatcher> CODEC = Codec.STRING.comapFlatMap(RecipeIdMatcher::parse, RecipeIdMatcher::toString);
        public static final Codec<List<RecipeIdMatcher>> LIST_CODEC = Codec.either(CODEC.listOf(), CODEC)
                .xmap(e -> e.map(l -> l, List::of), l -> com.mojang.datafixers.util.Either.left(l));

        public static DataResult<RecipeIdMatcher> parse(String raw) {
            if (raw.startsWith("@")) {
                String namespace = raw.substring(1);
                return !namespace.isEmpty() && ResourceLocation.isValidNamespace(namespace)
                        ? DataResult.success(new RecipeIdMatcher(namespace, null))
                        : DataResult.error(() -> "Invalid mod id: " + raw);
            }
            return ResourceLocation.read(raw).map(id -> new RecipeIdMatcher(null, id));
        }

        public boolean matches(ResourceLocation recipe) {
            return id != null ? id.equals(recipe) : recipe.getNamespace().equals(namespace);
        }

        @Override
        public String toString() {
            return id != null ? id.toString() : "@" + namespace;
        }
    }
}
