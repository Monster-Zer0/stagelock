package dev.stagelock.core.tools;

import dev.stagelock.StageLock;
import dev.stagelock.api.StageIds;
import dev.stagelock.core.stage.StageManager;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

/**
 * Migrates GameStages data ({@code <world>/playerdata/gamestages/<uuid>.dat}, compressed NBT with a
 * {@code Stages} string list) into StageLock's player scope.
 */
public final class GameStagesImport {

    public record Result(int players, int stages, int skipped) {
    }

    private GameStagesImport() {
    }

    public static Path directory(MinecraftServer server) {
        return server.getWorldPath(LevelResource.PLAYER_DATA_DIR).resolve("gamestages");
    }

    public static Result run(MinecraftServer server) throws IOException {
        Path dir = directory(server);
        if (!Files.isDirectory(dir)) return new Result(0, 0, 0);
        int players = 0;
        int stages = 0;
        int skipped = 0;
        try (Stream<Path> files = Files.list(dir)) {
            for (Path file : files.filter(f -> f.getFileName().toString().endsWith(".dat")).toList()) {
                UUID uuid;
                try {
                    uuid = UUID.fromString(file.getFileName().toString().replace(".dat", ""));
                } catch (IllegalArgumentException e) {
                    skipped++;
                    continue;
                }
                CompoundTag tag = NbtIo.readCompressed(file, NbtAccounter.unlimitedHeap());
                var list = tag.getList("Stages", Tag.TAG_STRING);
                List<String> valid = new ArrayList<>();
                for (int i = 0; i < list.size(); i++) {
                    String stage = StageIds.normalize(list.getString(i));
                    if (StageIds.isValid(stage)) valid.add(stage);
                    else {
                        skipped++;
                        StageLock.LOGGER.warn("GameStages import: skipping invalid stage '{}' for {}", stage, uuid);
                    }
                }
                stages += StageManager.get().grantPlayer(uuid, valid).size();
                players++;
            }
        }
        return new Result(players, stages, skipped);
    }
}
