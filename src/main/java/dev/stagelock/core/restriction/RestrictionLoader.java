package dev.stagelock.core.restriction;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.JsonOps;
import dev.stagelock.StageLock;
import dev.stagelock.api.event.CollectRulesEvent;
import dev.stagelock.api.restriction.Restriction;
import dev.stagelock.core.config.ServerConfig;
import dev.stagelock.core.definition.StageDefinitions;
import net.minecraft.core.HolderLookup;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.neoforged.neoforge.common.NeoForge;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * Loads {@code data/<namespace>/stagelock/restrictions/**.json}. A file holds one rule, or
 * {@code {"rules": [...]}} for several (ids get a {@code /<index>} suffix). Broken rules are logged
 * and skipped so one typo does not disable every restriction.
 */
public final class RestrictionLoader extends SimpleJsonResourceReloadListener {

    public static final String DIRECTORY = "stagelock/restrictions";

    private final HolderLookup.Provider registries;

    public RestrictionLoader(HolderLookup.Provider registries) {
        super(new Gson(), DIRECTORY);
        this.registries = registries;
    }

    @Override
    protected void apply(Map<ResourceLocation, JsonElement> files, ResourceManager resourceManager, ProfilerFiller profiler) {
        DynamicOps<JsonElement> ops = RegistryOps.create(JsonOps.INSTANCE, registries);
        Map<ResourceLocation, Restriction> rules = new LinkedHashMap<>();
        files.forEach((file, json) -> {
            if (json instanceof JsonObject object && object.get("rules") instanceof JsonArray array) {
                for (int i = 0; i < array.size(); i++) {
                    parse(ops, file.withSuffix("/" + i), array.get(i), rules);
                }
            } else {
                parse(ops, file, json, rules);
            }
        });
        NeoForge.EVENT_BUS.post(new CollectRulesEvent.Restrictions(registries, rules));
        validateStages(rules);
        RestrictionManager.server().setRules(rules);
        StageLock.LOGGER.info("Loaded {} restriction rules", rules.size());
    }

    /**
     * When stage definitions exist, rules mentioning undefined stages are probably typos: warn, or with
     * {@code strictStages} reject them. (Stage definitions load before restrictions.)
     */
    private static void validateStages(Map<ResourceLocation, Restriction> rules) {
        StageDefinitions definitions = StageDefinitions.server();
        if (definitions.all().isEmpty()) return;
        boolean strict = ServerConfig.SPEC.isLoaded() && ServerConfig.STRICT_STAGES.get();
        rules.entrySet().removeIf(entry -> {
            Set<String> unknown = new LinkedHashSet<>(entry.getValue().requires().stages());
            unknown.removeIf(definitions::isDefined);
            if (unknown.isEmpty()) return false;
            if (strict) {
                StageLock.LOGGER.error("Rejecting restriction {}: undefined stages {}", entry.getKey(), unknown);
                return true;
            }
            StageLock.LOGGER.warn("Restriction {} uses undefined stages {}", entry.getKey(), unknown);
            return false;
        });
    }

    private static void parse(DynamicOps<JsonElement> ops, ResourceLocation id, JsonElement json, Map<ResourceLocation, Restriction> out) {
        Restriction.CODEC.parse(ops, json)
                .ifSuccess(rule -> out.put(id, rule))
                .ifError(error -> StageLock.LOGGER.error("Skipping restriction {}: {}", id, error.message()));
    }
}
