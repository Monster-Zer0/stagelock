package dev.stagelock.api.restriction;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.stagelock.api.StageRequirement;
import net.minecraft.resources.ResourceLocation;

import java.util.List;

/**
 * {@code "type": "stagelock:viewer_category"} - hides whole recipe viewer categories (JEI, REI and EMI
 * tabs) until the requirement is met. Nothing is enforced in the world; this only affects what viewers show.
 * Category ids differ between viewers; {@code /stagelock_categories} lists the ones loaded.
 *
 * <pre>{@code
 * { "type": "stagelock:viewer_category", "requires": "mypack:iron_age", "categories": ["minecraft:anvil", "emi:anvil_repairing"] }
 * }</pre>
 */
public record ViewerCategoryRestriction(
        StageRequirement requires,
        List<ResourceLocation> categories
) implements Restriction {

    public static final MapCodec<ViewerCategoryRestriction> MAP_CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            StageRequirement.CODEC.fieldOf("requires").forGetter(ViewerCategoryRestriction::requires),
            Codec.either(ResourceLocation.CODEC.listOf(), ResourceLocation.CODEC)
                    .xmap(e -> e.map(l -> l, List::of), com.mojang.datafixers.util.Either::<List<ResourceLocation>, ResourceLocation>left)
                    .fieldOf("categories").forGetter(ViewerCategoryRestriction::categories)
    ).apply(i, ViewerCategoryRestriction::new));

    @Override
    public RestrictionType<?> type() {
        return RestrictionTypes.VIEWER_CATEGORY;
    }
}
