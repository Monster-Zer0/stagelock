package dev.stagelock.compat.kubejs;

import dev.latvian.mods.kubejs.stages.Stages;
import dev.stagelock.api.StageLockAPI;
import dev.stagelock.core.stage.StageManager;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

import java.util.Collection;
import java.util.List;

/**
 * Backs KubeJS's built-in {@code player.stages} with StageLock, so existing scripts (and mods that
 * use KubeJS stages, such as FTB Quests via FTB XMod Compat) read and write StageLock stages.
 * <p>
 * Writes go through StageLock, which syncs and fires events itself, so KubeJS's own stage packets
 * are never sent. On the client this is a read-only view of the synced stages.
 */
final class StageLockKubeStages implements Stages {

    private final Player player;

    StageLockKubeStages(Player player) {
        this.player = player;
    }

    @Override
    public Player getPlayer() {
        return player;
    }

    @Override
    public boolean addNoUpdate(String stage) {
        return player instanceof ServerPlayer server && StageManager.getOrNull() != null
                && !StageLockAPI.grant(server, stage).isEmpty();
    }

    @Override
    public boolean removeNoUpdate(String stage) {
        return player instanceof ServerPlayer server && StageManager.getOrNull() != null
                && !StageLockAPI.revoke(server, stage).isEmpty();
    }

    @Override
    public Collection<String> getAll() {
        return StageLockAPI.getStages(player).asSet();
    }

    @Override
    public boolean has(String stage) {
        return StageLockAPI.has(player, stage);
    }

    @Override
    public boolean add(String stage) {
        return addNoUpdate(stage);
    }

    @Override
    public boolean remove(String stage) {
        return removeNoUpdate(stage);
    }

    @Override
    public boolean clear() {
        return player instanceof ServerPlayer && StageManager.getOrNull() != null
                && !StageLockAPI.clear(player.getUUID()).isEmpty();
    }

    @Override
    public void sync() {
        if (player instanceof ServerPlayer && StageManager.getOrNull() != null) {
            StageManager.get().markChanged(List.of(player.getUUID()));
        }
    }
}
