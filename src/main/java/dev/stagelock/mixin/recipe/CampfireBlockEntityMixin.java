package dev.stagelock.mixin.recipe;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import dev.stagelock.core.recipe.RecipeContext;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CampfireCookingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.CampfireBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;

import java.util.Optional;

@Mixin(CampfireBlockEntity.class)
abstract class CampfireBlockEntityMixin {

    @WrapMethod(method = "cookTick")
    private static void stagelock$asOwner(Level level, BlockPos pos, BlockState state, CampfireBlockEntity blockEntity,
                                            Operation<Void> original) {
        RecipeContext.withOwner(blockEntity, () -> original.call(level, pos, state, blockEntity));
    }

    /** Placing food: the clicking player's context (set by the block use mixin) wins, else the owner. */
    @WrapMethod(method = "getCookableRecipe")
    private Optional<RecipeHolder<CampfireCookingRecipe>> stagelock$cookableAsOwner(ItemStack stack,
                                                                                     Operation<Optional<RecipeHolder<CampfireCookingRecipe>>> original) {
        return RecipeContext.withOwnerIfAbsent((CampfireBlockEntity) (Object) this, () -> original.call(stack));
    }
}
