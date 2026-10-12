package dev.stagelock.mixin.recipe;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import dev.stagelock.core.recipe.RecipeContext;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.StonecutterMenu;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(StonecutterMenu.class)
abstract class StonecutterMenuMixin {

    @Unique
    private Player stagelock$player;

    @Inject(method = "<init>(ILnet/minecraft/world/entity/player/Inventory;Lnet/minecraft/world/inventory/ContainerLevelAccess;)V", at = @At("TAIL"))
    private void stagelock$capturePlayer(int id, Inventory inventory, ContainerLevelAccess access, CallbackInfo ci) {
        this.stagelock$player = inventory.player;
    }

    @WrapMethod(method = "setupRecipeList")
    private void stagelock$asPlayer(Container container, ItemStack stack, Operation<Void> original) {
        if (stagelock$player == null) {
            original.call(container, stack);
        } else {
            RecipeContext.withPlayer(stagelock$player, () -> original.call(container, stack));
        }
    }
}
