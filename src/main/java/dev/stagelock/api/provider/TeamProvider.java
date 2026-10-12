package dev.stagelock.api.provider;

import net.minecraft.server.MinecraftServer;

import java.util.Collection;
import java.util.Optional;
import java.util.UUID;

/**
 * Maps players to teams so team-scoped stages can be shared.
 * Register custom providers with {@code StageLockAPI.registerTeamProvider}.
 */
public interface TeamProvider {

    /** Returns a stable team key for the player, e.g. {@code "vanilla:red"}. Empty if the player has no team. */
    Optional<String> teamOf(MinecraftServer server, UUID player);

    /** Returns the known members of the team (online or offline). */
    Collection<UUID> members(MinecraftServer server, String teamKey);

    /** Resolves a user-facing team name (as typed in a command) to a team key. */
    default Optional<String> resolveTeam(MinecraftServer server, String name) {
        return Optional.empty();
    }
}
