package dev.stagelock.api;

import java.util.Collection;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Immutable snapshot of a player's effective stages (player + team + global + contributed sources).
 * <p>
 * Every snapshot gets a unique {@link #version()}, so callers can cache derived decisions and
 * invalidate them by comparing versions instead of comparing sets.
 */
public final class StageSet {

    private static final AtomicLong VERSIONS = new AtomicLong();

    public static final StageSet EMPTY = new StageSet(Set.of());

    private final Set<String> stages;
    private final long version;

    private StageSet(Set<String> stages) {
        this.stages = stages;
        this.version = VERSIONS.incrementAndGet();
    }

    public static StageSet of(Collection<String> stages) {
        return stages.isEmpty() ? EMPTY : new StageSet(Set.copyOf(stages));
    }

    public boolean has(String stage) {
        return stages.contains(stage);
    }

    public boolean hasAll(Collection<String> required) {
        return stages.containsAll(required);
    }

    public boolean hasAny(Collection<String> candidates) {
        for (String stage : candidates) {
            if (stages.contains(stage)) return true;
        }
        return false;
    }

    public boolean test(StageRequirement requirement) {
        return requirement.test(this);
    }

    public Set<String> asSet() {
        return stages;
    }

    public int size() {
        return stages.size();
    }

    public long version() {
        return version;
    }

    @Override
    public String toString() {
        return "StageSet" + stages;
    }
}
