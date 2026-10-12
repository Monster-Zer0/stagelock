package dev.stagelock.mixin.recipe;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import dev.stagelock.core.recipe.RecipeContext;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.ItemCombinerMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.SmithingMenu;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(SmithingMenu.class)
abstract class SmithingMenuMixin extends ItemCombinerMenu {

    private SmithingMenuMixin(MenuType<?> type, int id, Inventory inventory, ContainerLevelAccess access) {
        super(type, id, inventory, access);
    }

    @WrapMethod(method = "createResult")
    private void stagelock$asPlayer(Operation<Void> original) {
        RecipeContext.withPlayer(this.player, () -> original.call());
    }
}
