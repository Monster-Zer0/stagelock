package dev.stagelock.compat.kubejs;

import dev.latvian.mods.kubejs.event.EventGroup;
import dev.latvian.mods.kubejs.event.EventHandler;
import dev.latvian.mods.kubejs.event.EventTargetType;
import dev.latvian.mods.kubejs.event.TargetedEventHandler;

public interface StageLockKubeEvents {

    EventGroup GROUP = EventGroup.of("StageLockEvents");

    EventHandler RESTRICTIONS = GROUP.server("restrictions", () -> RestrictionsKubeEvent.class);
    EventHandler STAGES = GROUP.server("stages", () -> StagesKubeEvent.class);
    TargetedEventHandler<String> GRANTED = GROUP.server("granted", () -> StageChangeKubeEvent.class).supportsTarget(EventTargetType.STRING);
    TargetedEventHandler<String> REVOKED = GROUP.server("revoked", () -> StageChangeKubeEvent.class).supportsTarget(EventTargetType.STRING);
}
