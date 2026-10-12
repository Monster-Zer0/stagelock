package dev.stagelock.compat.kubejs;

import dev.latvian.mods.kubejs.player.StageChangedEvent;
import dev.latvian.mods.kubejs.plugin.builtin.event.PlayerEvents;
import dev.latvian.mods.kubejs.script.ScriptType;
import dev.latvian.mods.kubejs.stages.StageCreationEvent;
import dev.latvian.mods.kubejs.stages.StageEvents;
import dev.stagelock.api.event.CollectRulesEvent;
import dev.stagelock.api.event.StageEvent;
import dev.stagelock.core.stage.StageManager;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.common.NeoForge;

import java.util.List;

/** Wires StageLock into KubeJS. Only loaded when KubeJS is installed. */
public final class KubeJSCompat {

    private KubeJSCompat() {
    }

    public static void init() {
        NeoForge.EVENT_BUS.addListener(StageCreationEvent.class, event -> event.setPlayerStages(new StageLockKubeStages(event.getEntity())));
        NeoForge.EVENT_BUS.addListener(CollectRulesEvent.Restrictions.class, event -> {
            if (!StageLockKubeEvents.RESTRICTIONS.hasListeners()) return;
            RestrictionsKubeEvent kubeEvent = new RestrictionsKubeEvent(event.getRegistries());
            StageLockKubeEvents.RESTRICTIONS.post(ScriptType.SERVER, kubeEvent);
            kubeEvent.collect(event.getRules());
        });
        NeoForge.EVENT_BUS.addListener(CollectRulesEvent.StageDefinitions.class, event -> {
            if (!StageLockKubeEvents.STAGES.hasListeners()) return;
            StagesKubeEvent kubeEvent = new StagesKubeEvent();
            StageLockKubeEvents.STAGES.post(ScriptType.SERVER, kubeEvent);
            kubeEvent.collect(event.getDefinitions());
        });
        NeoForge.EVENT_BUS.addListener(StageEvent.Granted.class, event -> onChange(event, true));
        NeoForge.EVENT_BUS.addListener(StageEvent.Revoked.class, event -> onChange(event, false));
    }

    private static void onChange(StageEvent event, boolean granted) {
        if (StageManager.getOrNull() == null) return;
        var handler = granted ? StageLockKubeEvents.GRANTED : StageLockKubeEvents.REVOKED;
        var builtin = granted ? PlayerEvents.STAGE_ADDED : PlayerEvents.STAGE_REMOVED;
        List<ServerPlayer> players = StageManager.get().affectedPlayers(event.getScope(), event.getTarget());
        if (handler.hasListeners(event.getStage())) {
            if (players.isEmpty()) {
                handler.post(ScriptType.SERVER, event.getStage(),
                        new StageChangeKubeEvent(event.getServer(), null, event.getStage(), event.getScope(), event.getTarget()));
            }
            for (ServerPlayer player : players) {
                handler.post(ScriptType.SERVER, event.getStage(),
                        new StageChangeKubeEvent(event.getServer(), player, event.getStage(), event.getScope(), event.getTarget()));
            }
        }
        // Keep KubeJS's own PlayerEvents.stageAdded / stageRemoved working.
        if (builtin.hasListeners(event.getStage())) {
            for (ServerPlayer player : players) {
                builtin.post(ScriptType.SERVER, event.getStage(), new StageChangedEvent(player, StageEvents.get(player), event.getStage()));
            }
        }
    }
}
