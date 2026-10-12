package dev.stagelock.core.recipe;

import dev.stagelock.api.StageLockAPI;
import dev.stagelock.api.StageSet;
import dev.stagelock.core.config.ServerConfig;
import dev.stagelock.core.enforce.Restrictions;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.common.CommonHooks;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.function.Supplier;

/**
 * Tells the recipe lookup whose stages to filter by. Callers that know the crafter (crafting menus,
 * owned furnaces) wrap their lookup in {@link #with}; the {@code RecipeManager} mixin reads
 * {@link #current} and hides locked recipes, so a locked recipe falls through to any unlocked recipe
 * with the same inputs.
 */
public final class RecipeContext {

    /** Pushed for crafters that must not be gated (exempt players, automationPolicy = ALLOW). */
    private static final Object UNRESTRICTED = new Object();

    private static final ThreadLocal<Deque<Object>> STACK = ThreadLocal.withInitial(ArrayDeque::new);

    private RecipeContext() {
    }

    public static <T> T with(@Nullable StageSet stages, Supplier<T> action) {
        Deque<Object> stack = STACK.get();
        stack.push(stages == null ? UNRESTRICTED : stages);
        try {
            return action.get();
        } finally {
            stack.pop();
        }
    }

    public static <T> T withPlayer(Player player, Supplier<T> action) {
        return with(stagesOf(player), action);
    }

    public static <T> T withOwner(BlockEntity blockEntity, Supplier<T> action) {
        return with(BlockOwner.stagesFor(blockEntity), action);
    }

    /** Runs with the block entity's owner only if no caller has already established a crafter. */
    public static <T> T withOwnerIfAbsent(BlockEntity blockEntity, Supplier<T> action) {
        return STACK.get().isEmpty() ? withOwner(blockEntity, action) : action.get();
    }

    @Nullable
    public static StageSet stagesOf(Player player) {
        return Restrictions.isExempt(player) ? null : StageLockAPI.getStages(player);
    }

    /**
     * The stages to filter by, or {@code null} to leave the lookup unfiltered.
     */
    @Nullable
    public static StageSet current(Level level) {
        Object top = STACK.get().peek();
        if (top == UNRESTRICTED) return null;
        if (top instanceof StageSet stages) return stages;
        if (level.isClientSide()) return null;
        // NeoForge sets this while a result slot is being taken; covers some modded grids.
        Player crafting = CommonHooks.getCraftingPlayer();
        if (crafting != null) return stagesOf(crafting);
        return ServerConfig.CONTEXTLESS_POLICY.get() == ServerConfig.Policy.DENY ? StageSet.EMPTY : null;
    }
}
