package dev.stagelock.api;

import dev.stagelock.api.provider.StageSource;
import dev.stagelock.api.provider.TeamProvider;
import dev.stagelock.client.ClientStageCache;
import dev.stagelock.core.stage.StageManager;
import dev.stagelock.core.team.TeamProviders;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Public entry point for other mods. Reads work on both sides (the client reads the synced cache);
 * writes are server-only.
 *
 * <pre>{@code
 * if (StageLockAPI.has(player, "iron_age")) { ... }
 * StageLockAPI.grant(serverPlayer, "iron_age");
 * StageLockAPI.global().grant("blood_moon");
 * }</pre>
 */
public final class StageLockAPI {

    private StageLockAPI() {
    }

    // ---------------------------------------------------------------- reads (both sides)

    /** The player's effective stages: player ∪ team ∪ global ∪ contributed sources. */
    public static StageSet getStages(Player player) {
        if (player.level().isClientSide()) return ClientStageCache.get();
        StageManager manager = StageManager.getOrNull();
        return manager == null ? StageSet.EMPTY : manager.stages(player);
    }

    public static boolean has(Player player, String stage) {
        return getStages(player).has(StageIds.normalize(stage));
    }

    public static boolean hasAll(Player player, String... stages) {
        return test(player, StageRequirement.allOf(stages));
    }

    public static boolean hasAny(Player player, String... stages) {
        return test(player, StageRequirement.anyOf(stages));
    }

    public static boolean test(Player player, StageRequirement requirement) {
        return requirement.test(getStages(player));
    }

    // ---------------------------------------------------------------- player scope (server)

    /** Grants stages to an online player. Returns the stages that were newly added. */
    public static Set<String> grant(ServerPlayer player, String... stages) {
        return StageManager.get().grantPlayer(player.getUUID(), Arrays.asList(stages));
    }

    /** Grants stages to a player who may be offline. */
    public static Set<String> grant(UUID player, Collection<String> stages) {
        return StageManager.get().grantPlayer(player, stages);
    }

    public static Set<String> revoke(ServerPlayer player, String... stages) {
        return StageManager.get().revokePlayer(player.getUUID(), Arrays.asList(stages));
    }

    public static Set<String> revoke(UUID player, Collection<String> stages) {
        return StageManager.get().revokePlayer(player, stages);
    }

    /** Removes all player-scoped stages (team and global stages are untouched). */
    public static Set<String> clear(UUID player) {
        return StageManager.get().clearPlayer(player);
    }

    /** Only the stages stored on the player itself, without team/global/sources. */
    public static Set<String> getPlayerStages(UUID player) {
        return StageManager.get().store().player(player);
    }

    // ---------------------------------------------------------------- team & global scopes (server)

    public static Scope global() {
        return new Scope() {
            @Override
            public Set<String> grant(String... stages) {
                return StageManager.get().grantGlobal(Arrays.asList(stages));
            }

            @Override
            public Set<String> revoke(String... stages) {
                return StageManager.get().revokeGlobal(Arrays.asList(stages));
            }

            @Override
            public Set<String> list() {
                return StageManager.get().store().global();
            }
        };
    }

    /** The team scope for a team key such as {@code "vanilla:red"}. */
    public static Scope team(String teamKey) {
        return new Scope() {
            @Override
            public Set<String> grant(String... stages) {
                return StageManager.get().grantTeam(teamKey, Arrays.asList(stages));
            }

            @Override
            public Set<String> revoke(String... stages) {
                return StageManager.get().revokeTeam(teamKey, Arrays.asList(stages));
            }

            @Override
            public Set<String> list() {
                return StageManager.get().store().team(teamKey);
            }
        };
    }

    /** The team scope of the player's current team, if they have one. */
    public static Optional<Scope> team(ServerPlayer player) {
        return StageManager.get().teamOf(player.getUUID()).map(StageLockAPI::team);
    }

    // ---------------------------------------------------------------- extension points

    public static void registerStageSource(StageSource source) {
        StageManager.registerSource(source);
    }

    public static void registerTeamProvider(String id, TeamProvider provider) {
        TeamProviders.register(id, provider);
    }

    /** Drops cached stages for the player; call when a {@link StageSource}'s answer changed. */
    public static void invalidate(UUID player) {
        StageManager manager = StageManager.getOrNull();
        if (manager != null) manager.markChanged(List.of(player));
    }

    public interface Scope {
        Set<String> grant(String... stages);

        Set<String> revoke(String... stages);

        Set<String> list();

        default boolean has(String stage) {
            return list().contains(StageIds.normalize(stage));
        }
    }
}
