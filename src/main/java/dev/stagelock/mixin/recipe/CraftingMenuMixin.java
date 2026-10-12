package dev.stagelock.mixin.recipe;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import dev.stagelock.core.recipe.RecipeContext;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeInput;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import java.util.Optional;

/** Crafting table and the 2x2 inventory grid (InventoryMenu calls the same static method). */
@Mixin(CraftingMenu.class)
abstract class CraftingMenuMixin {

    @WrapOperation(method = "slotChangedCraftingGrid", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/item/crafting/RecipeManager;getRecipeFor(Lnet/minecraft/world/item/crafting/RecipeType;Lnet/minecraft/world/item/crafting/RecipeInput;Lnet/minecraft/world/level/Level;Lnet/minecraft/world/item/crafting/RecipeHolder;)Ljava/util/Optional;"))
    private static Optional<?> stagelock$asPlayer(RecipeManager manager, RecipeType<?> type, RecipeInput input, Level level,
                                                   RecipeHolder<?> last, Operation<Optional<?>> original,
                                                   @Local(argsOnly = true) Player player) {
        return RecipeContext.withPlayer(player, () -> original.call(manager, type, input, level, last));
    }
}
