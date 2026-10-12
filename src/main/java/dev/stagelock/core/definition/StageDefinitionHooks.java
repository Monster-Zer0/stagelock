package dev.stagelock.core.definition;

import dev.stagelock.api.StageDefinition;
import dev.stagelock.api.StageScope;
import dev.stagelock.api.event.StageEvent;
import dev.stagelock.core.stage.StageManager;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.entity.player.AdvancementEvent;

import java.util.List;

/** Applies the behaviour declared in stage definitions: advancement unlocks and grant effects. */
public final class StageDefinitionHooks {

    private StageDefinitionHooks() {
    }

    public static void register(IEventBus bus) {
        bus.addListener(StageDefinitionHooks::onAdvancement);
        bus.addListener(StageDefinitionHooks::onGranted);
    }

    private static void onAdvancement(AdvancementEvent.AdvancementEarnEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || StageManager.getOrNull() == null) return;
        ResourceLocation advancement = event.getAdvancement().id();
        StageDefinitions.server().all().forEach((stage, definition) -> {
            StageDefinition.Unlock unlock = definition.unlock();
            if (!unlock.advancements().contains(advancement)) return;
            StageManager manager = StageManager.get();
            switch (unlock.scope()) {
                case PLAYER -> manager.grantPlayer(player.getUUID(), List.of(stage));
                case GLOBAL -> manager.grantGlobal(List.of(stage));
                case TEAM -> manager.teamOf(player.getUUID()).ifPresentOrElse(
                        team -> manager.grantTeam(team, List.of(stage)),
                        () -> manager.grantPlayer(player.getUUID(), List.of(stage)));
            }
        });
    }

    private static void onGranted(StageEvent.Granted event) {
        StageDefinition definition = StageDefinitions.server().get(event.getStage()).orElse(null);
        if (definition == null || StageManager.getOrNull() == null) return;
        StageDefinition.OnGrant onGrant = definition.onGrant();
        if (!onGrant.message() && onGrant.function().isEmpty()) return;
        MinecraftServer server = event.getServer();
        Component name = StageDefinitions.server().displayName(event.getStage());
        for (ServerPlayer player : StageManager.get().affectedPlayers(event.getScope(), event.getTarget())) {
            if (onGrant.message()) {
                player.sendSystemMessage(Component.translatable("message.stagelock.unlocked", name).withStyle(ChatFormatting.GOLD));
            }
            onGrant.function().flatMap(id -> server.getFunctions().get(id)).ifPresent(function ->
                    server.getFunctions().execute(function, player.createCommandSourceStack().withSuppressedOutput().withPermission(2)));
        }
    }

    /** Scope helper used by commands and tests. */
    public static boolean isPlayerScope(StageScope scope) {
        return scope == StageScope.PLAYER;
    }
}
