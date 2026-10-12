package dev.stagelock.api.event;

import dev.stagelock.api.StageScope;
import net.minecraft.server.MinecraftServer;
import net.neoforged.bus.api.Event;
import net.neoforged.bus.api.ICancellableEvent;

/**
 * Fired on the server (on {@code NeoForge.EVENT_BUS}) when a stage is granted or revoked in any scope.
 * <p>
 * {@link #getTarget()} is the player UUID string for {@link StageScope#PLAYER}, the team key for
 * {@link StageScope#TEAM}, and {@code "global"} for {@link StageScope#GLOBAL}.
 * <p>
 * Granting a stage that is already present, or revoking one that is absent, fires nothing.
 */
public abstract class StageEvent extends Event {

    private final MinecraftServer server;
    private final StageScope scope;
    private final String target;
    private final String stage;

    protected StageEvent(MinecraftServer server, StageScope scope, String target, String stage) {
        this.server = server;
        this.scope = scope;
        this.target = target;
        this.stage = stage;
    }

    public MinecraftServer getServer() {
        return server;
    }

    public StageScope getScope() {
        return scope;
    }

    public String getTarget() {
        return target;
    }

    public String getStage() {
        return stage;
    }

    /** Fired before a stage is granted. Cancel to prevent it. */
    public static class Grant extends StageEvent implements ICancellableEvent {
        public Grant(MinecraftServer server, StageScope scope, String target, String stage) {
            super(server, scope, target, stage);
        }
    }

    /** Fired after a stage has been granted. */
    public static class Granted extends StageEvent {
        public Granted(MinecraftServer server, StageScope scope, String target, String stage) {
            super(server, scope, target, stage);
        }
    }

    /** Fired before a stage is revoked. Cancel to prevent it. */
    public static class Revoke extends StageEvent implements ICancellableEvent {
        public Revoke(MinecraftServer server, StageScope scope, String target, String stage) {
            super(server, scope, target, stage);
        }
    }

    /** Fired after a stage has been revoked. */
    public static class Revoked extends StageEvent {
        public Revoked(MinecraftServer server, StageScope scope, String target, String stage) {
            super(server, scope, target, stage);
        }
    }
}
