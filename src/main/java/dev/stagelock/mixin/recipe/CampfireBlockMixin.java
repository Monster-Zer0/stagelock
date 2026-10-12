package dev.stagelock.mixin.recipe;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import dev.stagelock.core.recipe.RecipeContext;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.spongepowered.asm.mixin.Mixin;

/** Putting food on a campfire is gated by the player doing it. */
@Mixin(CampfireBlock.class)
abstract class CampfireBlockMixin {

    @WrapMethod(method = "useItemOn")
    private ItemInteractionResult stagelock$asPlayer(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
                                                       InteractionHand hand, BlockHitResult hit, Operation<ItemInteractionResult> original) {
        return RecipeContext.withPlayer(player, () -> original.call(stack, state, level, pos, player, hand, hit));
    }
}
