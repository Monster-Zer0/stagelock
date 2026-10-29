package dev.stagelock.api.restriction;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import dev.stagelock.StageLock;
import net.minecraft.resources.ResourceLocation;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Registry of restriction types. Addons register their own types during mod construction; the JSON
 * {@code "type"} field selects one.
 */
public final class RestrictionTypes {

    private static final Map<ResourceLocation, RestrictionType<?>> TYPES = new ConcurrentHashMap<>();

    public static final Codec<RestrictionType<?>> CODEC = ResourceLocation.CODEC.comapFlatMap(
            id -> {
                RestrictionType<?> type = TYPES.get(id);
                return type != null ? DataResult.success(type) : DataResult.error(() -> "Unknown restriction type " + id);
            },
            RestrictionType::id);

    public static final RestrictionType<ItemRestriction> ITEM = register(
            new RestrictionType<>(StageLock.id("item"), ItemRestriction.MAP_CODEC));

    public static final RestrictionType<RecipeRestriction> RECIPE = register(
            new RestrictionType<>(StageLock.id("recipe"), RecipeRestriction.MAP_CODEC));

    public static final RestrictionType<BlockRestriction> BLOCK = register(
            new RestrictionType<>(StageLock.id("block"), BlockRestriction.MAP_CODEC));

    public static final RestrictionType<DimensionRestriction> DIMENSION = register(
            new RestrictionType<>(StageLock.id("dimension"), DimensionRestriction.MAP_CODEC));

    public static final RestrictionType<EntityRestriction> ENTITY = register(
            new RestrictionType<>(StageLock.id("entity"), EntityRestriction.MAP_CODEC));

    public static final RestrictionType<FluidRestriction> FLUID = register(
            new RestrictionType<>(StageLock.id("fluid"), FluidRestriction.MAP_CODEC));

    public static final RestrictionType<EnchantmentRestriction> ENCHANTMENT = register(
            new RestrictionType<>(StageLock.id("enchantment"), EnchantmentRestriction.MAP_CODEC));

    public static final RestrictionType<ViewerCategoryRestriction> VIEWER_CATEGORY = register(
            new RestrictionType<>(StageLock.id("viewer_category"), ViewerCategoryRestriction.MAP_CODEC));

    private RestrictionTypes() {
    }

    public static <T extends Restriction> RestrictionType<T> register(RestrictionType<T> type) {
        if (TYPES.putIfAbsent(type.id(), type) != null) {
            throw new IllegalStateException("Duplicate restriction type " + type.id());
        }
        return type;
    }
}
