package dev.stagelock.mixin.recipe;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import dev.stagelock.core.recipe.RecipeContext;
import dev.stagelock.core.recipe.RecipeGate;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.CrafterMenu;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

import java.util.Optional;

/** The crafter's result preview shows what the viewing player could craft. */
@Mixin(CrafterMenu.class)
abstract class CrafterMenuMixin {

    @Shadow
    @Final
    private Player player;

    @ModifyExpressionValue(method = "refreshRecipeResult", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/block/CrafterBlock;getPotentialResults(Lnet/minecraft/world/level/Level;Lnet/minecraft/world/item/crafting/CraftingInput;)Ljava/util/Optional;"))
    private Optional<RecipeHolder<CraftingRecipe>> stagelock$filterForViewer(Optional<RecipeHolder<CraftingRecipe>> found) {
        return RecipeGate.filter(found, RecipeContext.stagesOf(player), player.level());
    }
}
