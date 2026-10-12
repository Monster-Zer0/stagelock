package dev.stagelock.mixin.client;

import dev.stagelock.client.HiddenNames;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ItemStack.class)
abstract class ItemStackMixin {

    @Inject(method = "getHoverName", at = @At("HEAD"), cancellable = true)
    private void stagelock$hideRestrictedName(CallbackInfoReturnable<Component> cir) {
        Component hidden = HiddenNames.get((ItemStack) (Object) this);
        if (hidden != null) cir.setReturnValue(hidden);
    }
}
