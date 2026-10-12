package dev.stagelock.api.restriction;

import com.mojang.serialization.MapCodec;
import net.minecraft.resources.ResourceLocation;

public record RestrictionType<T extends Restriction>(ResourceLocation id, MapCodec<T> codec) {
}
