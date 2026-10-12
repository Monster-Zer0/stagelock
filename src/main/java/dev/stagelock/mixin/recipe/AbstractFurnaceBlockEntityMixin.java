package dev.stagelock.mixin.recipe;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import dev.stagelock.core.recipe.RecipeContext;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;

/** Furnaces, smokers and blast furnaces smelt with their owner's stages. */
@Mixin(AbstractFurnaceBlockEntity.class)
abstract class AbstractFurnaceBlockEntityMixin {

    @WrapMethod(method = "serverTick")
    private static void stagelock$asOwner(Level level, BlockPos pos, BlockState state, AbstractFurnaceBlockEntity blockEntity,
                                            Operation<Void> original) {
        RecipeContext.withOwner(blockEntity, () -> original.call(level, pos, state, blockEntity));
    }

    @WrapMethod(method = "getTotalCookTime")
    private static int stagelock$cookTimeAsOwner(Level level, AbstractFurnaceBlockEntity blockEntity, Operation<Integer> original) {
        return RecipeContext.withOwnerIfAbsent(blockEntity, () -> original.call(level, blockEntity));
    }
}
