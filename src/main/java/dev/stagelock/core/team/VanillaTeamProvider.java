package dev.stagelock.core.team;

import com.mojang.authlib.GameProfile;
import dev.stagelock.api.provider.TeamProvider;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.scores.PlayerTeam;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Teams from the vanilla scoreboard ({@code /team}). Team keys look like {@code vanilla:<team name>}. */
public final class VanillaTeamProvider implements TeamProvider {

    public static final String PREFIX = "vanilla:";

    @Override
    public Optional<String> teamOf(MinecraftServer server, UUID player) {
        return nameOf(server, player)
                .map(name -> server.getScoreboard().getPlayersTeam(name))
                .map(team -> PREFIX + team.getName());
    }

    @Override
    public Collection<UUID> members(MinecraftServer server, String teamKey) {
        if (!teamKey.startsWith(PREFIX)) return List.of();
        PlayerTeam team = server.getScoreboard().getPlayerTeam(teamKey.substring(PREFIX.length()));
        if (team == null) return List.of();
        List<UUID> members = new ArrayList<>();
        for (String name : team.getPlayers()) {
            ServerPlayer online = server.getPlayerList().getPlayerByName(name);
            if (online != null) {
                members.add(online.getUUID());
            } else if (server.getProfileCache() != null) {
                server.getProfileCache().get(name).map(GameProfile::getId).ifPresent(members::add);
            }
        }
        return members;
    }

    @Override
    public Optional<String> resolveTeam(MinecraftServer server, String name) {
        String plain = name.startsWith(PREFIX) ? name.substring(PREFIX.length()) : name;
        return Optional.ofNullable(server.getScoreboard().getPlayerTeam(plain)).map(t -> PREFIX + t.getName());
    }

    private static Optional<String> nameOf(MinecraftServer server, UUID player) {
        ServerPlayer online = server.getPlayerList().getPlayer(player);
        if (online != null) return Optional.of(online.getScoreboardName());
        if (server.getProfileCache() == null) return Optional.empty();
        return server.getProfileCache().get(player).map(GameProfile::getName);
    }
}
