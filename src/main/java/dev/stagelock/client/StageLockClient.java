package dev.stagelock.client;

import dev.stagelock.StageLock;
import dev.stagelock.api.StageSet;
import dev.stagelock.client.viewer.ViewerRefresher;
import dev.stagelock.core.config.ClientConfig;
import dev.stagelock.core.definition.StageDefinitions;
import dev.stagelock.core.restriction.RestrictionManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.CustomizeGuiOverlayEvent;
import net.neoforged.neoforge.common.NeoForge;

import java.util.Map;

@Mod(value = StageLock.MOD_ID, dist = Dist.CLIENT)
public final class StageLockClient {

    public StageLockClient(IEventBus modBus, ModContainer container) {
        container.registerConfig(ModConfig.Type.CLIENT, ClientConfig.SPEC);
        NeoForge.EVENT_BUS.addListener(ClientPlayerNetworkEvent.LoggingOut.class, event -> {
            ClientStageCache.reset();
            RestrictionManager.client().setRules(Map.of());
            StageDefinitions.client().set(Map.of());
        });
        NeoForge.EVENT_BUS.addListener(TooltipHandler::onTooltip);
        ViewerRefresher.init();
        BlockDisguises.init();
        modBus.addListener(LockDecorator::register);
        NeoForge.EVENT_BUS.addListener(CustomizeGuiOverlayEvent.DebugText.class, StageLockClient::debugText);
    }

    /** F3 shows the stage count; holding Shift in F3 lists them. */
    private static void debugText(CustomizeGuiOverlayEvent.DebugText event) {
        if (!Minecraft.getInstance().getDebugOverlay().showDebugScreen()) return;
        StageSet stages = ClientStageCache.get();
        event.getRight().add("");
        event.getRight().add("[StageLock] " + stages.size() + " stages");
        if (Screen.hasShiftDown()) {
            stages.asSet().stream().sorted().forEach(s -> event.getRight().add("  " + s));
        }
    }
}
