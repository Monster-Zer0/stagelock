package dev.stagelock.compat.ftbteams;

import dev.ftb.mods.ftbteams.api.FTBTeamsAPI;
import dev.ftb.mods.ftbteams.api.Team;
import dev.ftb.mods.ftbteams.api.event.TeamEvent;
import dev.stagelock.api.provider.TeamProvider;
import dev.stagelock.core.stage.StageManager;
import dev.stagelock.core.team.TeamProviders;
import net.minecraft.server.MinecraftServer;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Team stages from FTB Teams party teams. Team keys look like {@code ftbteams:<team uuid>}. */
public final class FtbTeamsCompat implements TeamProvider {

    public static final String ID = "ftbteams";
    private static final String PREFIX = ID + ":";

    private FtbTeamsCompat() {
    }

    public static void init() {
        TeamProviders.register(ID, new FtbTeamsCompat());
        TeamEvent.PLAYER_JOINED_PARTY.register(event -> changed(event.getPlayer().getUUID()));
        TeamEvent.PLAYER_LEFT_PARTY.register(event -> changed(event.getPlayerId()));
    }

    private static void changed(UUID player) {
        StageManager manager = StageManager.getOrNull();
        if (manager != null) manager.markChanged(List.of(player));
    }

    @Override
    public Optional<String> teamOf(MinecraftServer server, UUID player) {
        if (!FTBTeamsAPI.api().isManagerLoaded()) return Optional.empty();
        return FTBTeamsAPI.api().getManager().getTeamForPlayerID(player)
                .filter(Team::isPartyTeam)
                .map(team -> PREFIX + team.getId());
    }

    @Override
    public Collection<UUID> members(MinecraftServer server, String teamKey) {
        return byKey(teamKey).map(team -> (Collection<UUID>) List.copyOf(team.getMembers())).orElse(List.of());
    }

    @Override
    public Optional<String> resolveTeam(MinecraftServer server, String name) {
        if (!FTBTeamsAPI.api().isManagerLoaded()) return Optional.empty();
        Optional<Team> team = name.startsWith(PREFIX) ? byKey(name) : FTBTeamsAPI.api().getManager().getTeamByName(name);
        return team.filter(Team::isPartyTeam).map(t -> PREFIX + t.getId());
    }

    private static Optional<Team> byKey(String teamKey) {
        if (!teamKey.startsWith(PREFIX) || !FTBTeamsAPI.api().isManagerLoaded()) return Optional.empty();
        try {
            return FTBTeamsAPI.api().getManager().getTeamByID(UUID.fromString(teamKey.substring(PREFIX.length())));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }
}
