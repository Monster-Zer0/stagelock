package dev.stagelock.core.enforce;

import dev.stagelock.api.StageLockAPI;
import dev.stagelock.api.restriction.ItemAction;
import dev.stagelock.api.restriction.ItemRestriction;
import dev.stagelock.core.config.ServerConfig;
import dev.stagelock.core.restriction.RestrictionManager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/** Side-aware entry point used by every enforcer: bypass rules + stage lookup + rule index. */
public final class Restrictions {

    private Restrictions() {
    }

    public static boolean isExempt(Player player) {
        if (!ServerConfig.SPEC.isLoaded()) return false;
        if (ServerConfig.CREATIVE_BYPASS.get() && player.isCreative()) return true;
        if (ServerConfig.SPECTATOR_BYPASS.get() && player.isSpectator()) return true;
        int level = ServerConfig.OP_BYPASS_LEVEL.get();
        return level > 0 && player.hasPermissions(level);
    }

    /** The rule blocking {@code action} on {@code stack} for this player, or {@code null} if allowed. */
    @Nullable
    public static RestrictionManager.Entry<ItemRestriction> item(Player player, ItemStack stack, @Nullable ItemAction action) {
        if (stack.isEmpty() || isExempt(player)) return null;
        return RestrictionManager.of(player).items().find(stack, StageLockAPI.getStages(player), action);
    }

    public static boolean isBlocked(Player player, ItemStack stack, ItemAction action) {
        return item(player, stack, action) != null;
    }
}
