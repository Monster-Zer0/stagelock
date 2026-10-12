package dev.stagelock.core.stage;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Persistent storage for all three stage scopes, saved with the overworld as
 * {@code data/stagelock_stages.dat}.
 * <p>
 * Keeping player stages here (instead of in a player attachment) lets commands edit offline players
 * and lets block entities read their owner's stages while the owner is offline.
 */
public final class StageStore extends SavedData {

    private static final String NAME = "stagelock_stages";

    private final Map<UUID, Set<String>> players = new HashMap<>();
    private final Map<String, Set<String>> teams = new HashMap<>();
    private final Set<String> global = new LinkedHashSet<>();

    public static StageStore get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(StageStore::new, StageStore::load), NAME);
    }

    public Set<String> player(UUID player) {
        return Collections.unmodifiableSet(players.getOrDefault(player, Set.of()));
    }

    public Set<String> team(String teamKey) {
        return Collections.unmodifiableSet(teams.getOrDefault(teamKey, Set.of()));
    }

    public Set<String> global() {
        return Collections.unmodifiableSet(global);
    }

    public Set<UUID> knownPlayers() {
        return Collections.unmodifiableSet(players.keySet());
    }

    /** Every stage present in any scope; used for command suggestions. */
    public Set<String> allStages() {
        Set<String> all = new LinkedHashSet<>(global);
        players.values().forEach(all::addAll);
        teams.values().forEach(all::addAll);
        return all;
    }

    boolean addPlayer(UUID player, String stage) {
        return mark(players.computeIfAbsent(player, k -> new LinkedHashSet<>()).add(stage));
    }

    boolean removePlayer(UUID player, String stage) {
        Set<String> set = players.get(player);
        if (set == null || !set.remove(stage)) return false;
        if (set.isEmpty()) players.remove(player);
        return mark(true);
    }

    boolean addTeam(String team, String stage) {
        return mark(teams.computeIfAbsent(team, k -> new LinkedHashSet<>()).add(stage));
    }

    boolean removeTeam(String team, String stage) {
        Set<String> set = teams.get(team);
        if (set == null || !set.remove(stage)) return false;
        if (set.isEmpty()) teams.remove(team);
        return mark(true);
    }

    boolean addGlobal(String stage) {
        return mark(global.add(stage));
    }

    boolean removeGlobal(String stage) {
        return mark(global.remove(stage));
    }

    private boolean mark(boolean changed) {
        if (changed) setDirty();
        return changed;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        CompoundTag playerTag = new CompoundTag();
        players.forEach((uuid, stages) -> playerTag.put(uuid.toString(), writeSet(stages)));
        CompoundTag teamTag = new CompoundTag();
        teams.forEach((team, stages) -> teamTag.put(team, writeSet(stages)));
        tag.put("players", playerTag);
        tag.put("teams", teamTag);
        tag.put("global", writeSet(global));
        return tag;
    }

    private static StageStore load(CompoundTag tag, HolderLookup.Provider registries) {
        StageStore store = new StageStore();
        CompoundTag playerTag = tag.getCompound("players");
        for (String key : playerTag.getAllKeys()) {
            try {
                store.players.put(UUID.fromString(key), readSet(playerTag.getList(key, Tag.TAG_STRING)));
            } catch (IllegalArgumentException ignored) {
                // Skip malformed UUID keys rather than failing the whole load.
            }
        }
        CompoundTag teamTag = tag.getCompound("teams");
        for (String key : teamTag.getAllKeys()) {
            store.teams.put(key, readSet(teamTag.getList(key, Tag.TAG_STRING)));
        }
        store.global.addAll(readSet(tag.getList("global", Tag.TAG_STRING)));
        return store;
    }

    private static ListTag writeSet(Set<String> stages) {
        ListTag list = new ListTag();
        stages.forEach(s -> list.add(StringTag.valueOf(s)));
        return list;
    }

    private static Set<String> readSet(ListTag list) {
        Set<String> set = new LinkedHashSet<>();
        for (int i = 0; i < list.size(); i++) set.add(list.getString(i));
        return set;
    }
}
