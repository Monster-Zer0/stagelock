package dev.stagelock.api.event;

import dev.stagelock.api.StageSet;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

import java.util.Set;

/**
 * Fired once per player per tick when that player's effective stages changed, no matter which scope
 * caused it. Fires on the server after the change is flushed, and on the client after the sync
 * packet is applied. Use this to refresh caches, UIs and recipe viewers.
 */
public class StagesChangedEvent extends PlayerEvent {

    private final StageSet stages;
    private final Set<String> added;
    private final Set<String> removed;

    public StagesChangedEvent(Player player, StageSet stages, Set<String> added, Set<String> removed) {
        super(player);
        this.stages = stages;
        this.added = Set.copyOf(added);
        this.removed = Set.copyOf(removed);
    }

    public StageSet getStages() {
        return stages;
    }

    public Set<String> getAdded() {
        return added;
    }

    public Set<String> getRemoved() {
        return removed;
    }
}
