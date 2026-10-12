package dev.stagelock.client;

import dev.stagelock.api.StageSet;
import dev.stagelock.api.event.StagesChangedEvent;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.common.NeoForge;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * The local player's stages as last synced by the server. The client never decides anything on its
 * own; this cache only drives tooltips, recipe viewers and client-side prediction.
 * <p>
 * Contains no client-only class references in its signatures, so common code may call {@link #get()}.
 */
public final class ClientStageCache {

    private static volatile StageSet stages = StageSet.EMPTY;

    private ClientStageCache() {
    }

    public static StageSet get() {
        return stages;
    }

    /** Called on the main thread by the payload handler. */
    public static void apply(Set<String> synced) {
        StageSet before = stages;
        StageSet now = StageSet.of(synced);
        stages = now;
        Set<String> added = new LinkedHashSet<>(now.asSet());
        added.removeAll(before.asSet());
        Set<String> removed = new LinkedHashSet<>(before.asSet());
        removed.removeAll(now.asSet());
        Player player = Minecraft.getInstance().player;
        if (player != null && (!added.isEmpty() || !removed.isEmpty())) {
            NeoForge.EVENT_BUS.post(new StagesChangedEvent(player, now, added, removed));
        }
    }

    public static void reset() {
        stages = StageSet.EMPTY;
    }
}
