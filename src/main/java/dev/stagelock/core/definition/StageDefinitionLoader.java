package dev.stagelock.core.definition;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.mojang.serialization.JsonOps;
import dev.stagelock.StageLock;
import dev.stagelock.api.StageDefinition;
import dev.stagelock.api.event.CollectRulesEvent;
import net.minecraft.core.HolderLookup;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.neoforged.neoforge.common.NeoForge;

import java.util.LinkedHashMap;
import java.util.Map;

/** Loads {@code data/<namespace>/stagelock/stages/**.json}. */
public final class StageDefinitionLoader extends SimpleJsonResourceReloadListener {

    private final HolderLookup.Provider registries;

    public StageDefinitionLoader(HolderLookup.Provider registries) {
        super(new Gson(), "stagelock/stages");
        this.registries = registries;
    }

    @Override
    protected void apply(Map<ResourceLocation, JsonElement> files, ResourceManager resourceManager, ProfilerFiller profiler) {
        var ops = RegistryOps.create(JsonOps.INSTANCE, registries);
        Map<String, StageDefinition> definitions = new LinkedHashMap<>();
        files.forEach((file, json) -> StageDefinition.CODEC.parse(ops, json)
                .ifSuccess(def -> definitions.put(def.id().orElse(file.toString()), def))
                .ifError(error -> StageLock.LOGGER.error("Skipping stage definition {}: {}", file, error.message())));
        NeoForge.EVENT_BUS.post(new CollectRulesEvent.StageDefinitions(registries, definitions));
        StageDefinitions.server().set(definitions);
        StageLock.LOGGER.info("Loaded {} stage definitions", definitions.size());
    }
}
