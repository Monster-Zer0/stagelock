package dev.stagelock.mixin.recipe;

import dev.stagelock.api.StageSet;
import dev.stagelock.core.recipe.RecipeContext;
import dev.stagelock.core.recipe.RecipeGate;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.RecipeCraftingHolder;
import net.minecraft.world.inventory.ResultSlot;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.crafting.RecipeHolder;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Safety net: a locked recipe's output can never be taken, even if another mod filled the slot directly. */
@Mixin(Slot.class)
abstract class ResultSlotMixin {

    @Inject(method = "mayPickup", at = @At("HEAD"), cancellable = true)
    private void stagelock$denyLocked(Player player, CallbackInfoReturnable<Boolean> cir) {
        if (!((Object) this instanceof ResultSlot) || player.level().isClientSide()
                || !(((Slot) (Object) this).container instanceof RecipeCraftingHolder holder)) return;
        RecipeHolder<?> used = holder.getRecipeUsed();
        StageSet stages = RecipeContext.stagesOf(player);
        if (used != null && stages != null && RecipeGate.isLocked(used, stages, player.level())) {
            cir.setReturnValue(false);
        }
    }
}
