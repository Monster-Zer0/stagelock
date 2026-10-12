package dev.stagelock.mixin.client;

import dev.stagelock.client.BlockDisguises;
import net.minecraft.client.renderer.block.BlockModelShaper;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Map;

/** Renders disguised blocks with their disguise's model. */
@Mixin(BlockModelShaper.class)
abstract class BlockModelShaperMixin {

    @Shadow
    private Map<BlockState, BakedModel> modelByStateCache;

    @Inject(method = "getBlockModel", at = @At("HEAD"), cancellable = true)
    private void stagelock$disguise(BlockState state, CallbackInfoReturnable<BakedModel> cir) {
        BlockState disguise = BlockDisguises.get(state.getBlock());
        if (disguise != null) {
            BakedModel model = modelByStateCache.get(disguise);
            if (model != null) cir.setReturnValue(model);
        }
    }
}
