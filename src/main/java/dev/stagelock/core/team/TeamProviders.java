package dev.stagelock.core.team;

import dev.stagelock.api.provider.TeamProvider;
import dev.stagelock.core.config.ServerConfig;
import net.minecraft.server.MinecraftServer;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public final class TeamProviders {

    public static final String VANILLA = "vanilla";

    private static final TeamProvider NONE = new TeamProvider() {
        @Override
        public Optional<String> teamOf(MinecraftServer server, UUID player) {
            return Optional.empty();
        }

        @Override
        public Collection<UUID> members(MinecraftServer server, String teamKey) {
            return List.of();
        }
    };

    /** Registered providers in priority order; AUTO picks the first non-vanilla one. */
    private static final Map<String, TeamProvider> PROVIDERS = new LinkedHashMap<>();

    static {
        PROVIDERS.put(VANILLA, new VanillaTeamProvider());
    }

    private TeamProviders() {
    }

    public static synchronized void register(String id, TeamProvider provider) {
        PROVIDERS.put(id, provider);
    }

    public static TeamProvider current() {
        return switch (ServerConfig.TEAM_PROVIDER.get()) {
            case NONE -> NONE;
            case VANILLA -> PROVIDERS.get(VANILLA);
            case FTBTEAMS -> PROVIDERS.getOrDefault("ftbteams", NONE);
            case AUTO -> PROVIDERS.entrySet().stream()
                    .filter(e -> !e.getKey().equals(VANILLA))
                    .map(Map.Entry::getValue)
                    .findFirst()
                    .orElse(PROVIDERS.get(VANILLA));
        };
    }
}
