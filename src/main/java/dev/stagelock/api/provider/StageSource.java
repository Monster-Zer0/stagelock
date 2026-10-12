package dev.stagelock.api.provider;

import net.minecraft.server.MinecraftServer;

import java.util.Collection;
import java.util.UUID;

/**
 * Lets another mod contribute extra stages to a player's effective stage set (for example, a skills
 * mod granting stages by skill level). Contributed stages are read-only.
 * <p>
 * Results are cached; call {@code StageLockAPI.invalidate(player)} when a source's answer changes.
 */
@FunctionalInterface
public interface StageSource {

    Collection<String> getStages(MinecraftServer server, UUID player);
}
