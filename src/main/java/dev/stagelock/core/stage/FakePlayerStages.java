package dev.stagelock.core.stage;

import dev.stagelock.StageLock;
import dev.stagelock.api.StageIds;
import dev.stagelock.api.StageSet;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Parsed form of the {@code fakePlayers} config list: {@code "<name or uuid>=stage1,stage2"}. */
record FakePlayerStages(Map<String, StageSet> byKey, StageSet fallback) {

    static final FakePlayerStages EMPTY = new FakePlayerStages(Map.of(), StageSet.EMPTY);

    static FakePlayerStages parse(List<? extends String> entries) {
        Map<String, StageSet> byKey = new HashMap<>();
        StageSet fallback = StageSet.EMPTY;
        for (String entry : entries) {
            int split = entry.indexOf('=');
            if (split < 0) {
                StageLock.LOGGER.warn("Ignoring fake player entry without '=': {}", entry);
                continue;
            }
            String key = entry.substring(0, split).trim();
            List<String> stages = new ArrayList<>();
            for (String raw : entry.substring(split + 1).split(",")) {
                if (raw.isBlank()) continue;
                StageIds.validate(raw).resultOrPartial(StageLock.LOGGER::warn).ifPresent(stages::add);
            }
            if (key.equals("*")) {
                fallback = StageSet.of(stages);
            } else {
                byKey.put(key.toLowerCase(java.util.Locale.ROOT), StageSet.of(stages));
            }
        }
        return new FakePlayerStages(Map.copyOf(byKey), fallback);
    }

    StageSet get(String name, UUID uuid) {
        StageSet byUuid = byKey.get(uuid.toString());
        if (byUuid != null) return byUuid;
        return byKey.getOrDefault(name.toLowerCase(java.util.Locale.ROOT), fallback);
    }
}
