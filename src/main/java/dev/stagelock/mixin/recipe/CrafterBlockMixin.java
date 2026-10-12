package dev.stagelock.mixin.recipe;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import dev.stagelock.core.recipe.BlockOwner;
import dev.stagelock.core.recipe.RecipeGate;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.block.CrafterBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import java.util.Optional;

/**
 * The crafter caches recipe lookups per input across all crafters, so its result is filtered after
 * the cache rather than inside the lookup (which would poison the shared cache).
 */
@Mixin(CrafterBlock.class)
abstract class CrafterBlockMixin {

    @ModifyExpressionValue(method = "dispenseFrom", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/block/CrafterBlock;getPotentialResults(Lnet/minecraft/world/level/Level;Lnet/minecraft/world/item/crafting/CraftingInput;)Ljava/util/Optional;"))
    private Optional<RecipeHolder<CraftingRecipe>> stagelock$filterForOwner(Optional<RecipeHolder<CraftingRecipe>> found,
                                                                             BlockState state, ServerLevel level, BlockPos pos) {
        BlockEntity blockEntity = level.getBlockEntity(pos);
        return blockEntity == null ? found : RecipeGate.filter(found, BlockOwner.stagesFor(blockEntity), level);
    }
}
