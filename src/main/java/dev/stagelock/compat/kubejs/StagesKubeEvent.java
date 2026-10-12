package dev.stagelock.compat.kubejs;

import dev.latvian.mods.kubejs.event.KubeEvent;
import dev.stagelock.api.StageDefinition;
import dev.stagelock.api.StageIds;
import dev.stagelock.api.StageScope;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * {@code StageLockEvents.stages(event => event.create('iron_age').displayName('Iron Age').requires('stone_age'))}
 */
public final class StagesKubeEvent implements KubeEvent {

    private final Map<String, StageBuilder> builders = new LinkedHashMap<>();

    void collect(Map<String, StageDefinition> out) {
        builders.forEach((id, builder) -> out.put(id, builder.build()));
    }

    public StageBuilder create(String stage) {
        String id = StageIds.validate(stage).getOrThrow(IllegalArgumentException::new);
        return builders.computeIfAbsent(id, k -> new StageBuilder());
    }

    public static final class StageBuilder {
        private Optional<Component> displayName = Optional.empty();
        private Optional<Component> description = Optional.empty();
        private Optional<ResourceLocation> icon = Optional.empty();
        private final List<String> requires = new ArrayList<>();
        private final List<ResourceLocation> advancements = new ArrayList<>();
        private StageScope scope = StageScope.PLAYER;
        private boolean message;
        private Optional<ResourceLocation> function = Optional.empty();

        public StageBuilder displayName(String name) {
            this.displayName = Optional.of(Component.literal(name));
            return this;
        }

        public StageBuilder description(String text) {
            this.description = Optional.of(Component.literal(text));
            return this;
        }

        public StageBuilder icon(String item) {
            this.icon = Optional.of(ResourceLocation.parse(item));
            return this;
        }

        public StageBuilder requires(String... stages) {
            Arrays.stream(stages).map(s -> StageIds.validate(s).getOrThrow(IllegalArgumentException::new)).forEach(requires::add);
            return this;
        }

        public StageBuilder unlockOnAdvancement(String advancement) {
            advancements.add(ResourceLocation.parse(advancement));
            return this;
        }

        /** player, team or global: where advancement unlocks grant the stage. */
        public StageBuilder scope(String scope) {
            this.scope = StageScope.valueOf(scope.toUpperCase(Locale.ROOT));
            return this;
        }

        public StageBuilder announce() {
            this.message = true;
            return this;
        }

        public StageBuilder runFunction(String function) {
            this.function = Optional.of(ResourceLocation.parse(function));
            return this;
        }

        StageDefinition build() {
            return new StageDefinition(Optional.empty(), displayName, description, icon, List.copyOf(requires),
                    new StageDefinition.Unlock(List.copyOf(advancements), scope), new StageDefinition.OnGrant(message, function));
        }
    }
}
