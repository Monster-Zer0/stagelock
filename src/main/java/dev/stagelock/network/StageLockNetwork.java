package dev.stagelock.network;

import dev.stagelock.client.ClientStageCache;
import dev.stagelock.core.definition.StageDefinitions;
import dev.stagelock.core.restriction.RestrictionManager;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

public final class StageLockNetwork {

    private static final String VERSION = "1";

    private StageLockNetwork() {
    }

    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar(VERSION);
        registrar.playToClient(SyncStagesPayload.TYPE, SyncStagesPayload.STREAM_CODEC,
                (payload, context) -> ClientStageCache.apply(payload.stages()));
        registrar.playToClient(SyncRestrictionsPayload.TYPE, SyncRestrictionsPayload.STREAM_CODEC,
                (payload, context) -> RestrictionManager.client().setRules(payload.rules()));
        registrar.playToClient(SyncDefinitionsPayload.TYPE, SyncDefinitionsPayload.STREAM_CODEC,
                (payload, context) -> StageDefinitions.client().set(payload.definitions()));
    }
}
