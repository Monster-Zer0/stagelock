package dev.stagelock.compat.kubejs;

import dev.latvian.mods.kubejs.event.KubeEvent;
import dev.stagelock.api.StageLockAPI;
import dev.stagelock.api.StageScope;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;

import java.util.Set;

/**
 * {@code StageLockEvents.granted('iron_age', event => event.player.tell('Welcome to the Iron Age'))}.
 * Fires once per affected online player; for team and global grants, also once with no player if nobody
 * affected is online.
 */
public final class StageChangeKubeEvent implements KubeEvent {

    private final MinecraftServer server;
    @Nullable
    private final ServerPlayer player;
    private final String stage;
    private final StageScope scope;
    private final String target;

    StageChangeKubeEvent(MinecraftServer server, @Nullable ServerPlayer player, String stage, StageScope scope, String target) {
        this.server = server;
        this.player = player;
        this.stage = stage;
        this.scope = scope;
        this.target = target;
    }

    public MinecraftServer getServer() {
        return server;
    }

    @Nullable
    public ServerPlayer getPlayer() {
        return player;
    }

    public String getStage() {
        return stage;
    }

    /** "player", "team" or "global". */
    public String getScope() {
        return scope.getSerializedName();
    }

    /** Player UUID, team key, or "global". */
    public String getTarget() {
        return target;
    }

    public Set<String> getPlayerStages() {
        return player == null ? Set.of() : StageLockAPI.getStages(player).asSet();
    }
}
