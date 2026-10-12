package dev.stagelock.api.restriction;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.stagelock.api.StageRequirement;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.resources.ResourceLocation;

import java.util.List;
import java.util.Optional;

/**
 * {@code "type": "stagelock:dimension"} - stops players (and vehicles carrying them) from entering.
 *
 * <pre>{@code
 * { "type": "stagelock:dimension", "requires": "mypack:nether", "dimensions": ["minecraft:the_nether"], "message": "Too hot!" }
 * }</pre>
 */
public record DimensionRestriction(
        StageRequirement requires,
        List<ResourceLocation> dimensions,
        Optional<Component> message
) implements Restriction {

    public static final MapCodec<DimensionRestriction> MAP_CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            StageRequirement.CODEC.fieldOf("requires").forGetter(DimensionRestriction::requires),
            com.mojang.serialization.Codec.either(ResourceLocation.CODEC.listOf(), ResourceLocation.CODEC)
                    .xmap(e -> e.map(l -> l, List::of), com.mojang.datafixers.util.Either::<List<ResourceLocation>, ResourceLocation>left)
                    .fieldOf("dimensions").forGetter(DimensionRestriction::dimensions),
            ComponentSerialization.CODEC.optionalFieldOf("message").forGetter(DimensionRestriction::message)
    ).apply(i, DimensionRestriction::new));

    @Override
    public RestrictionType<?> type() {
        return RestrictionTypes.DIMENSION;
    }
}
