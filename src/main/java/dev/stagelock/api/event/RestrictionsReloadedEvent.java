package dev.stagelock.api.event;

import net.neoforged.bus.api.Event;

/**
 * Fired on {@code NeoForge.EVENT_BUS} after the active restriction rules changed: on the server after
 * a datapack (re)load, on the client after the server synced them.
 */
public class RestrictionsReloadedEvent extends Event {

    private final boolean clientSide;

    public RestrictionsReloadedEvent(boolean clientSide) {
        this.clientSide = clientSide;
    }

    public boolean isClientSide() {
        return clientSide;
    }
}
