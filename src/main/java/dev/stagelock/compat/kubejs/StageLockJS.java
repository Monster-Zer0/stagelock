package dev.stagelock.compat.kubejs;

import dev.stagelock.api.StageLockAPI;
import dev.stagelock.api.StageRequirement;
import dev.stagelock.api.restriction.ItemAction;
import dev.stagelock.core.enforce.Restrictions;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import java.util.Locale;
import java.util.Set;

/**
 * The {@code StageLock} global in scripts.
 *
 * <pre>{@code
 * if (StageLock.has(player, 'iron_age')) ...
 * StageLock.grant(player, 'iron_age')
 * StageLock.grantGlobal('blood_moon')
 * StageLock.grantTeam(player, 'outpost')
 * StageLock.test(player, {any_of: ['a', 'b']})
 * StageLock.isLocked(player, item, 'use')
 * }</pre>
 */
public final class StageLockJS {

    private StageLockJS() {
    }

    public static boolean has(Player player, String stage) {
        return StageLockAPI.has(player, stage);
    }

    public static boolean test(Player player, Object requirement) {
        StageRequirement parsed = StageRequirement.CODEC.parse(com.mojang.serialization.JsonOps.INSTANCE, JsValues.toJson(requirement))
                .getOrThrow(IllegalArgumentException::new);
        return StageLockAPI.test(player, parsed);
    }

    public static Set<String> stages(Player player) {
        return StageLockAPI.getStages(player).asSet();
    }

    public static boolean grant(ServerPlayer player, String stage) {
        return !StageLockAPI.grant(player, stage).isEmpty();
    }

    public static boolean revoke(ServerPlayer player, String stage) {
        return !StageLockAPI.revoke(player, stage).isEmpty();
    }

    public static void clear(ServerPlayer player) {
        StageLockAPI.clear(player.getUUID());
    }

    public static boolean grantGlobal(String stage) {
        return !StageLockAPI.global().grant(stage).isEmpty();
    }

    public static boolean revokeGlobal(String stage) {
        return !StageLockAPI.global().revoke(stage).isEmpty();
    }

    public static boolean hasGlobal(String stage) {
        return StageLockAPI.global().has(stage);
    }

    /** Grants to the player's team; returns false if the player has no team. */
    public static boolean grantTeam(ServerPlayer player, String stage) {
        return StageLockAPI.team(player).map(team -> !team.grant(stage).isEmpty()).orElse(false);
    }

    public static boolean revokeTeam(ServerPlayer player, String stage) {
        return StageLockAPI.team(player).map(team -> !team.revoke(stage).isEmpty()).orElse(false);
    }

    /** Whether any rule locks this item for the player. */
    public static boolean isLocked(Player player, ItemStack stack) {
        return Restrictions.item(player, stack, null) != null;
    }

    /** Whether the item is locked for one action: pickup, use, place, attack, mine, equip, hold, craft, viewer. */
    public static boolean isLocked(Player player, ItemStack stack, String action) {
        return Restrictions.isBlocked(player, stack, ItemAction.valueOf(action.toUpperCase(Locale.ROOT)));
    }
}
