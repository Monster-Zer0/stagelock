package dev.stagelock.api.restriction;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.stagelock.api.StageRequirement;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.world.level.material.Fluid;

import java.util.List;
import java.util.Optional;

/**
 * {@code "type": "stagelock:fluid"} - blocks picking up fluids with containers (buckets), emptying
 * containers of them, and shows/hides them in recipe viewers.
 *
 * <pre>{@code
 * { "type": "stagelock:fluid", "requires": "mypack:lava_tech", "fluids": ["#minecraft:lava"],
 *   "actions": { "pickup": true, "place": true, "viewer": "hide" } }
 * }</pre>
 */
public record FluidRestriction(
        StageRequirement requires,
        List<RegistryMatcher<Fluid>> fluids,
        Actions actions,
        Optional<Component> message
) implements Restriction {

    public static final MapCodec<FluidRestriction> MAP_CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            StageRequirement.CODEC.fieldOf("requires").forGetter(FluidRestriction::requires),
            RegistryMatcher.listCodec(Registries.FLUID).fieldOf("fluids").forGetter(FluidRestriction::fluids),
            Actions.CODEC.optionalFieldOf("actions", Actions.DEFAULT).forGetter(FluidRestriction::actions),
            ComponentSerialization.CODEC.optionalFieldOf("message").forGetter(FluidRestriction::message)
    ).apply(i, FluidRestriction::new));

    @Override
    public RestrictionType<?> type() {
        return RestrictionTypes.FLUID;
    }

    public boolean matches(Fluid fluid) {
        return RegistryMatcher.anyMatch(fluids, fluid.builtInRegistryHolder());
    }

    public record Actions(boolean pickup, boolean place, ItemRestriction.ViewerMode viewer) {

        public static final Actions DEFAULT = new Actions(true, true, ItemRestriction.ViewerMode.HIDE);

        public static final Codec<Actions> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.BOOL.optionalFieldOf("pickup", true).forGetter(Actions::pickup),
                Codec.BOOL.optionalFieldOf("place", true).forGetter(Actions::place),
                ItemRestriction.ViewerMode.CODEC.optionalFieldOf("viewer", ItemRestriction.ViewerMode.HIDE).forGetter(Actions::viewer)
        ).apply(i, Actions::new));
    }
}
