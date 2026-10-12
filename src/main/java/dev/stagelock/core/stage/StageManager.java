package dev.stagelock.core.stage;

import dev.stagelock.StageLock;
import dev.stagelock.api.StageIds;
import dev.stagelock.api.StageScope;
import dev.stagelock.api.StageSet;
import dev.stagelock.api.event.StageEvent;
import dev.stagelock.api.event.StagesChangedEvent;
import dev.stagelock.api.provider.StageSource;
import dev.stagelock.core.config.ServerConfig;
import dev.stagelock.core.definition.StageDefinitions;
import dev.stagelock.core.team.TeamProviders;
import dev.stagelock.network.SyncStagesPayload;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.ICancellableEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BiPredicate;

/**
 * Server-side owner of all stage state. Computes and caches effective stage sets, applies grants and
 * revokes (firing {@link StageEvent}s), and batches client syncs to once per player per tick.
 */
public final class StageManager {

    private static final int TEAM_RECHECK_INTERVAL = 100;
    private static final List<StageSource> SOURCES = new CopyOnWriteArrayList<>();

    @Nullable
    private static StageManager instance;

    private final MinecraftServer server;
    private final Map<UUID, StageSet> cache = new HashMap<>();
    private final Map<UUID, Optional<String>> teams = new HashMap<>();
    private final Map<UUID, StageSet> lastSynced = new HashMap<>();
    private final Set<UUID> pending = new HashSet<>();
    private FakePlayerStages fakePlayers = FakePlayerStages.EMPTY;

    private StageManager(MinecraftServer server) {
        this.server = server;
    }

    public static void start(MinecraftServer server) {
        instance = new StageManager(server);
    }

    public static void stop() {
        instance = null;
    }

    @Nullable
    public static StageManager getOrNull() {
        return instance;
    }

    public static StageManager get() {
        if (instance == null) throw new IllegalStateException("StageLock stages are only available while a server is running");
        return instance;
    }

    public static void registerSource(StageSource source) {
        SOURCES.add(source);
    }

    public MinecraftServer server() {
        return server;
    }

    public StageStore store() {
        return StageStore.get(server);
    }

    // ---------------------------------------------------------------- reading

    public StageSet stages(Player player) {
        if (player instanceof FakePlayer fake) {
            return fakePlayers().get(fake.getGameProfile().getName(), fake.getUUID());
        }
        return stages(player.getUUID());
    }

    public StageSet stages(UUID player) {
        StageSet cached = cache.get(player);
        if (cached != null) return cached;
        StageSet computed = compute(player);
        cache.put(player, computed);
        return computed;
    }

    private StageSet compute(UUID player) {
        StageStore store = store();
        Set<String> all = new LinkedHashSet<>(store.player(player));
        Optional<String> team = TeamProviders.current().teamOf(server, player);
        teams.put(player, team);
        team.ifPresent(key -> all.addAll(store.team(key)));
        all.addAll(store.global());
        for (StageSource source : SOURCES) {
            try {
                source.getStages(server, player).forEach(s -> all.add(StageIds.normalize(s)));
            } catch (RuntimeException e) {
                StageLock.LOGGER.error("Stage source {} failed for {}", source, player, e);
            }
        }
        return StageSet.of(all);
    }

    public Optional<String> teamOf(UUID player) {
        return TeamProviders.current().teamOf(server, player);
    }

    // ---------------------------------------------------------------- writing

    /** Grants stages to a player (online or offline). Returns the stages that were actually added. */
    public Set<String> grantPlayer(UUID player, Collection<String> stages) {
        return apply(StageScope.PLAYER, player.toString(), stages, true,
                (s, stage) -> store().addPlayer(player, stage), List.of(player));
    }

    public Set<String> revokePlayer(UUID player, Collection<String> stages) {
        return apply(StageScope.PLAYER, player.toString(), stages, false,
                (s, stage) -> store().removePlayer(player, stage), List.of(player));
    }

    public Set<String> clearPlayer(UUID player) {
        return revokePlayer(player, Set.copyOf(store().player(player)));
    }

    public Set<String> grantTeam(String teamKey, Collection<String> stages) {
        return apply(StageScope.TEAM, teamKey, stages, true,
                (s, stage) -> store().addTeam(teamKey, stage), teamMembersOnline(teamKey));
    }

    public Set<String> revokeTeam(String teamKey, Collection<String> stages) {
        return apply(StageScope.TEAM, teamKey, stages, false,
                (s, stage) -> store().removeTeam(teamKey, stage), teamMembersOnline(teamKey));
    }

    public Set<String> grantGlobal(Collection<String> stages) {
        return apply(StageScope.GLOBAL, "global", stages, true,
                (s, stage) -> store().addGlobal(stage), onlinePlayers());
    }

    public Set<String> revokeGlobal(Collection<String> stages) {
        return apply(StageScope.GLOBAL, "global", stages, false,
                (s, stage) -> store().removeGlobal(stage), onlinePlayers());
    }

    private Set<String> apply(StageScope scope, String target, Collection<String> stages, boolean grant,
                              BiPredicate<StageScope, String> mutation, Collection<UUID> affected) {
        Set<String> changed = new LinkedHashSet<>();
        for (String stage : grant ? withPrerequisites(scope, target, stages) : normalize(stages)) {
            if (grant ? alreadyHas(scope, target, stage) : !alreadyHas(scope, target, stage)) continue;
            ICancellableEvent pre = grant
                    ? NeoForge.EVENT_BUS.post(new StageEvent.Grant(server, scope, target, stage))
                    : NeoForge.EVENT_BUS.post(new StageEvent.Revoke(server, scope, target, stage));
            if (pre.isCanceled()) continue;
            if (!mutation.test(scope, stage)) continue;
            changed.add(stage);
            NeoForge.EVENT_BUS.post(grant
                    ? new StageEvent.Granted(server, scope, target, stage)
                    : new StageEvent.Revoked(server, scope, target, stage));
        }
        if (!changed.isEmpty()) markChanged(affected);
        return changed;
    }

    private static Set<String> normalize(Collection<String> stages) {
        Set<String> out = new LinkedHashSet<>();
        for (String raw : stages) out.add(StageIds.validate(raw).getOrThrow(IllegalArgumentException::new));
        return out;
    }

    /** Applies {@code dependencyMode}: grants missing prerequisites first, or drops stages whose prerequisites are missing. */
    private Set<String> withPrerequisites(StageScope scope, String target, Collection<String> stages) {
        Set<String> requested = normalize(stages);
        ServerConfig.DependencyMode mode = ServerConfig.DEPENDENCY_MODE.get();
        if (mode == ServerConfig.DependencyMode.IGNORE) return requested;
        StageDefinitions definitions = StageDefinitions.server();
        Set<String> out = new LinkedHashSet<>();
        for (String stage : requested) {
            Set<String> parents = definitions.ancestors(stage);
            if (mode == ServerConfig.DependencyMode.GRANT_PARENTS) {
                out.addAll(parents);
                out.add(stage);
            } else {
                Set<String> present = scopeView(scope, target);
                if (present.containsAll(parents) || out.containsAll(parents)) {
                    out.add(stage);
                } else {
                    StageLock.LOGGER.info("Not granting {} to {} {}: missing prerequisites {}", stage, scope, target, parents);
                }
            }
        }
        return out;
    }

    /** What counts as "already has" for prerequisite checks: the scope's own stages plus everything inherited. */
    private Set<String> scopeView(StageScope scope, String target) {
        return switch (scope) {
            case PLAYER -> stages(UUID.fromString(target)).asSet();
            case TEAM -> {
                Set<String> set = new LinkedHashSet<>(store().team(target));
                set.addAll(store().global());
                yield set;
            }
            case GLOBAL -> store().global();
        };
    }

    /** Online players whose effective stages include the given scope target. */
    public List<ServerPlayer> affectedPlayers(StageScope scope, String target) {
        return switch (scope) {
            case PLAYER -> {
                ServerPlayer player = server.getPlayerList().getPlayer(UUID.fromString(target));
                yield player == null ? List.of() : List.of(player);
            }
            case TEAM -> server.getPlayerList().getPlayers().stream()
                    .filter(p -> teamOf(p.getUUID()).filter(target::equals).isPresent()).toList();
            case GLOBAL -> List.copyOf(server.getPlayerList().getPlayers());
        };
    }

    private boolean alreadyHas(StageScope scope, String target, String stage) {
        StageStore store = store();
        return switch (scope) {
            case PLAYER -> store.player(UUID.fromString(target)).contains(stage);
            case TEAM -> store.team(target).contains(stage);
            case GLOBAL -> store.global().contains(stage);
        };
    }

    /** Drops cached stages for the players and schedules a client sync at the end of the tick. */
    public void markChanged(Collection<UUID> players) {
        for (UUID player : players) {
            cache.remove(player);
            pending.add(player);
        }
    }

    public void invalidateAll() {
        cache.clear();
        pending.addAll(onlinePlayers());
    }

    private Collection<UUID> teamMembersOnline(String teamKey) {
        Set<UUID> members = new HashSet<>();
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (TeamProviders.current().teamOf(server, player.getUUID()).filter(teamKey::equals).isPresent()) {
                members.add(player.getUUID());
            }
        }
        return members;
    }

    private Collection<UUID> onlinePlayers() {
        return server.getPlayerList().getPlayers().stream().map(Player::getUUID).toList();
    }

    // ---------------------------------------------------------------- lifecycle hooks

    public void onLogin(ServerPlayer player) {
        cache.remove(player.getUUID());
        lastSynced.remove(player.getUUID());
        pending.add(player.getUUID());
        flush(player);
    }

    public void onLogout(ServerPlayer player) {
        cache.remove(player.getUUID());
        teams.remove(player.getUUID());
        lastSynced.remove(player.getUUID());
        pending.remove(player.getUUID());
    }

    public void onConfigReload() {
        fakePlayers = FakePlayerStages.parse(ServerConfig.FAKE_PLAYERS.get());
        invalidateAll();
    }

    private FakePlayerStages fakePlayers() {
        if (fakePlayers == FakePlayerStages.EMPTY && !ServerConfig.FAKE_PLAYERS.get().isEmpty()) {
            fakePlayers = FakePlayerStages.parse(ServerConfig.FAKE_PLAYERS.get());
        }
        return fakePlayers;
    }

    public void tick() {
        if (server.getTickCount() % TEAM_RECHECK_INTERVAL == 0) recheckTeams();
        if (pending.isEmpty()) return;
        for (UUID id : Set.copyOf(pending)) {
            ServerPlayer player = server.getPlayerList().getPlayer(id);
            if (player != null) flush(player);
        }
        pending.clear();
    }

    /** Catches team membership changes from providers that have no change events (vanilla scoreboard). */
    private void recheckTeams() {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            Optional<String> known = teams.get(player.getUUID());
            if (known == null) continue;
            Optional<String> now = TeamProviders.current().teamOf(server, player.getUUID());
            if (!now.equals(known)) markChanged(List.of(player.getUUID()));
        }
    }

    private void flush(ServerPlayer player) {
        pending.remove(player.getUUID());
        StageSet now = stages(player.getUUID());
        StageSet before = lastSynced.put(player.getUUID(), now);
        // Vanilla clients (and GameTest mock players) don't have our channel.
        if (player.connection.hasChannel(SyncStagesPayload.TYPE)) {
            PacketDistributor.sendToPlayer(player, new SyncStagesPayload(now.asSet()));
        }
        if (before == null) return;
        Set<String> added = new LinkedHashSet<>(now.asSet());
        added.removeAll(before.asSet());
        Set<String> removed = new LinkedHashSet<>(before.asSet());
        removed.removeAll(now.asSet());
        if (!added.isEmpty() || !removed.isEmpty()) {
            NeoForge.EVENT_BUS.post(new StagesChangedEvent(player, now, added, removed));
        }
    }
}
