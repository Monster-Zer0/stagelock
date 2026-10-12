package dev.stagelock.core.definition;

import dev.stagelock.api.StageDefinition;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;

import java.util.ArrayDeque;
import java.util.Collections;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/** Active stage definitions; one copy per logical side (the client's is synced from the server). */
public final class StageDefinitions {

    private static final StageDefinitions SERVER = new StageDefinitions();
    private static final StageDefinitions CLIENT = new StageDefinitions();

    private Map<String, StageDefinition> definitions = Map.of();

    public static StageDefinitions server() {
        return SERVER;
    }

    public static StageDefinitions client() {
        return CLIENT;
    }

    public static StageDefinitions of(Player player) {
        return player.level().isClientSide() ? CLIENT : SERVER;
    }

    public void set(Map<String, StageDefinition> definitions) {
        this.definitions = Collections.unmodifiableMap(new LinkedHashMap<>(definitions));
    }

    public Map<String, StageDefinition> all() {
        return definitions;
    }

    public Optional<StageDefinition> get(String stage) {
        return Optional.ofNullable(definitions.get(stage));
    }

    public boolean isDefined(String stage) {
        return definitions.containsKey(stage);
    }

    public Component displayName(String stage) {
        return get(stage).flatMap(StageDefinition::displayName).orElseGet(() -> Component.literal(stage));
    }

    /** The stage's prerequisites (transitively), ordered so parents come before children. Cycle-safe. */
    public Set<String> ancestors(String stage) {
        Set<String> out = new LinkedHashSet<>();
        Set<String> visiting = new LinkedHashSet<>();
        visit(stage, out, visiting);
        out.remove(stage);
        return out;
    }

    private void visit(String stage, Set<String> out, Set<String> visiting) {
        if (out.contains(stage) || !visiting.add(stage)) return;
        Deque<String> parents = new ArrayDeque<>(get(stage).map(StageDefinition::requires).orElse(java.util.List.of()));
        for (String parent : parents) visit(parent, out, visiting);
        out.add(stage);
    }
}
