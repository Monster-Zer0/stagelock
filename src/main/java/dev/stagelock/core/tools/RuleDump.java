package dev.stagelock.core.tools;

import com.google.gson.GsonBuilder;
import com.mojang.serialization.JsonOps;
import dev.stagelock.api.StageDefinition;
import dev.stagelock.api.restriction.Restriction;
import dev.stagelock.core.definition.StageDefinitions;
import dev.stagelock.core.restriction.RestrictionManager;
import dev.stagelock.core.stage.StageStore;
import net.minecraft.resources.RegistryOps;
import net.minecraft.server.MinecraftServer;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.TreeMap;

/** Writes every active stage definition and rule (JSON and KubeJS alike) to a Markdown file. */
public final class RuleDump {

    private RuleDump() {
    }

    public static Path write(MinecraftServer server, Path file) throws IOException {
        var gson = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
        var ops = RegistryOps.create(JsonOps.INSTANCE, server.registryAccess());
        StringBuilder out = new StringBuilder("# StageLock dump\n\n");

        StageStore store = StageStore.get(server);
        out.append("Players with stages: ").append(store.knownPlayers().size())
                .append(" | Global stages: ").append(store.global().isEmpty() ? "-" : String.join(", ", store.global()))
                .append("\n\n## Stage definitions\n\n");
        Map<String, StageDefinition> definitions = new TreeMap<>(StageDefinitions.server().all());
        if (definitions.isEmpty()) out.append("_none_\n");
        definitions.forEach((id, def) -> out.append("- `").append(id).append("` ")
                .append(def.displayName().map(c -> c.getString()).orElse(""))
                .append(def.requires().isEmpty() ? "" : " (requires " + String.join(", ", def.requires()) + ")")
                .append('\n'));

        out.append("\n## Restrictions\n");
        Map<String, Restriction> rules = new TreeMap<>();
        RestrictionManager.server().rules().forEach((id, rule) -> rules.put(id.toString(), rule));
        rules.forEach((id, rule) -> {
            out.append("\n### ").append(id).append("\n\n```json\n");
            String json = Restriction.CODEC.encodeStart(ops, rule).mapOrElse(gson::toJson, e -> "// not encodable: " + e.message());
            out.append(json);
            out.append("\n```\n");
        });

        Files.createDirectories(file.getParent());
        Files.writeString(file, out);
        return file;
    }
}
