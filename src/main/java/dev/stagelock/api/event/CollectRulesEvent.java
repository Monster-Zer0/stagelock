package dev.stagelock.api.event;

import dev.stagelock.api.StageDefinition;
import dev.stagelock.api.restriction.Restriction;
import net.minecraft.core.HolderLookup;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.Event;

import java.util.Map;

/**
 * Fired on the server during every datapack (re)load, after JSON files are read, so mods and scripts
 * can add rules in code. Added entries behave exactly like JSON ones.
 */
public abstract class CollectRulesEvent extends Event {

    private final HolderLookup.Provider registries;

    protected CollectRulesEvent(HolderLookup.Provider registries) {
        this.registries = registries;
    }

    public HolderLookup.Provider getRegistries() {
        return registries;
    }

    public static class Restrictions extends CollectRulesEvent {
        private final Map<ResourceLocation, Restriction> rules;

        public Restrictions(HolderLookup.Provider registries, Map<ResourceLocation, Restriction> rules) {
            super(registries);
            this.rules = rules;
        }

        public void add(ResourceLocation id, Restriction rule) {
            rules.put(id, rule);
        }

        public Map<ResourceLocation, Restriction> getRules() {
            return rules;
        }
    }

    public static class StageDefinitions extends CollectRulesEvent {
        private final Map<String, StageDefinition> definitions;

        public StageDefinitions(HolderLookup.Provider registries, Map<String, StageDefinition> definitions) {
            super(registries);
            this.definitions = definitions;
        }

        public void add(String stage, StageDefinition definition) {
            definitions.put(stage, definition);
        }

        public Map<String, StageDefinition> getDefinitions() {
            return definitions;
        }
    }
}
