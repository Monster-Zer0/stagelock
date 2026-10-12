package dev.stagelock.mixin.recipe;

import dev.stagelock.api.StageSet;
import dev.stagelock.core.recipe.RecipeContext;
import dev.stagelock.core.recipe.RecipeGate;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeInput;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/** Hides locked recipes from lookups made inside a {@link RecipeContext}. Without a context this is vanilla. */
@Mixin(RecipeManager.class)
abstract class RecipeManagerMixin {

    @Shadow
    protected abstract <I extends RecipeInput, T extends Recipe<I>> Collection<RecipeHolder<T>> byType(RecipeType<T> type);

    @Inject(method = "getRecipeFor(Lnet/minecraft/world/item/crafting/RecipeType;Lnet/minecraft/world/item/crafting/RecipeInput;Lnet/minecraft/world/level/Level;Lnet/minecraft/world/item/crafting/RecipeHolder;)Ljava/util/Optional;",
            at = @At("HEAD"), cancellable = true)
    private <I extends RecipeInput, T extends Recipe<I>> void stagelock$filterLookup(
            RecipeType<T> type, I input, Level level, RecipeHolder<T> last, CallbackInfoReturnable<Optional<RecipeHolder<T>>> cir) {
        if (input.isEmpty()) return;
        StageSet stages = RecipeContext.current(level);
        if (stages == null) return;
        cir.setReturnValue(RecipeGate.find(byType(type), last, input, level, stages));
    }

    @Inject(method = "getRecipesFor", at = @At("RETURN"), cancellable = true)
    private <I extends RecipeInput, T extends Recipe<I>> void stagelock$filterList(
            RecipeType<T> type, I input, Level level, CallbackInfoReturnable<List<RecipeHolder<T>>> cir) {
        StageSet stages = RecipeContext.current(level);
        if (stages == null) return;
        cir.setReturnValue(cir.getReturnValue().stream().filter(h -> !RecipeGate.isLocked(h, stages, level)).toList());
    }
}
