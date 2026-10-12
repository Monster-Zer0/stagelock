package dev.stagelock.mixin.client;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.stagelock.api.StageSet;
import dev.stagelock.core.recipe.RecipeContext;
import dev.stagelock.core.recipe.RecipeGate;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.recipebook.RecipeCollection;
import net.minecraft.stats.RecipeBook;
import net.minecraft.world.item.crafting.RecipeHolder;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Locked recipes count as unknown to the recipe book, so they are not shown or auto-filled. */
@Mixin(RecipeCollection.class)
abstract class RecipeCollectionMixin {

    @WrapOperation(method = {"updateKnownRecipes", "canCraft"}, at = @At(value = "INVOKE",
            target = "Lnet/minecraft/stats/RecipeBook;contains(Lnet/minecraft/world/item/crafting/RecipeHolder;)Z"))
    private boolean stagelock$hideLocked(RecipeBook book, RecipeHolder<?> recipe, Operation<Boolean> original) {
        if (!original.call(book, recipe)) return false;
        var player = Minecraft.getInstance().player;
        if (player == null) return true;
        StageSet stages = RecipeContext.stagesOf(player);
        return stages == null || !RecipeGate.isLocked(recipe, stages, player.level());
    }
}
