package dev.stagelock;

import com.mojang.logging.LogUtils;
import dev.stagelock.core.command.StageLockCommands;
import dev.stagelock.core.command.StageArgument;
import dev.stagelock.core.config.CommonConfig;
import dev.stagelock.core.config.ServerConfig;
import dev.stagelock.compat.curios.CuriosCompat;
import dev.stagelock.compat.ftbteams.FtbTeamsCompat;
import dev.stagelock.compat.kubejs.KubeJSCompat;
import dev.stagelock.core.StageLockRegistries;
import dev.stagelock.core.definition.StageDefinitionHooks;
import dev.stagelock.core.definition.StageDefinitionLoader;
import dev.stagelock.core.definition.StageDefinitions;
import dev.stagelock.core.enforce.InventoryScanner;
import dev.stagelock.core.enforce.ItemEnforcer;
import dev.stagelock.core.enforce.WorldEnforcer;
import dev.stagelock.core.recipe.BlockOwner;
import dev.stagelock.core.recipe.MenuRefresher;
import dev.stagelock.core.restriction.RestrictionLoader;
import dev.stagelock.core.restriction.RestrictionManager;
import dev.stagelock.core.stage.StageManager;
import dev.stagelock.network.StageLockNetwork;
import dev.stagelock.network.SyncDefinitionsPayload;
import dev.stagelock.network.SyncRestrictionsPayload;
import net.minecraft.commands.synchronization.ArgumentTypeInfo;
import net.minecraft.commands.synchronization.ArgumentTypeInfos;
import net.minecraft.commands.synchronization.SingletonArgumentInfo;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackSource;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.AddPackFindersEvent;
import net.neoforged.neoforge.event.AddReloadListenerEvent;
import net.neoforged.neoforge.event.OnDatapackSyncEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.TagsUpdatedEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.registries.DeferredRegister;
import org.slf4j.Logger;

@Mod(StageLock.MOD_ID)
public final class StageLock {

    public static final String MOD_ID = "stagelock";
    public static final Logger LOGGER = LogUtils.getLogger();

    private static final DeferredRegister<ArgumentTypeInfo<?, ?>> ARGUMENT_TYPES =
            DeferredRegister.create(Registries.COMMAND_ARGUMENT_TYPE, MOD_ID);

    static {
        ARGUMENT_TYPES.register("stage", () -> ArgumentTypeInfos.registerByClass(
                StageArgument.class, SingletonArgumentInfo.contextFree(StageArgument::stage)));
    }

    public StageLock(IEventBus modBus, ModContainer container) {
        container.registerConfig(ModConfig.Type.COMMON, CommonConfig.SPEC);
        container.registerConfig(ModConfig.Type.SERVER, ServerConfig.SPEC);

        ARGUMENT_TYPES.register(modBus);
        modBus.addListener(StageLockNetwork::register);
        modBus.addListener(ModConfigEvent.Reloading.class, event -> {
            if (event.getConfig().getSpec() == ServerConfig.SPEC && StageManager.getOrNull() != null) {
                StageManager.get().onConfigReload();
            }
        });

        IEventBus bus = NeoForge.EVENT_BUS;
        bus.addListener(ServerStartingEvent.class, event -> StageManager.start(event.getServer()));
        bus.addListener(ServerStoppedEvent.class, event -> StageManager.stop());
        bus.addListener(ServerTickEvent.Post.class, event -> {
            StageManager manager = StageManager.getOrNull();
            if (manager != null) manager.tick();
        });
        bus.addListener(PlayerEvent.PlayerLoggedInEvent.class, event -> {
            if (event.getEntity() instanceof ServerPlayer player) StageManager.get().onLogin(player);
        });
        bus.addListener(PlayerEvent.PlayerLoggedOutEvent.class, event -> {
            if (event.getEntity() instanceof ServerPlayer player && StageManager.getOrNull() != null) {
                StageManager.get().onLogout(player);
            }
        });
        bus.addListener(RegisterCommandsEvent.class, event -> StageLockCommands.register(event.getDispatcher()));

        // Definitions first: the restriction loader validates stage names against them.
        bus.addListener(AddReloadListenerEvent.class, event -> {
            event.addListener(new StageDefinitionLoader(event.getRegistryAccess()));
            event.addListener(new RestrictionLoader(event.getRegistryAccess()));
        });
        bus.addListener(OnDatapackSyncEvent.class, event -> {
            var definitions = new SyncDefinitionsPayload(StageDefinitions.server().all());
            var rules = new SyncRestrictionsPayload(RestrictionManager.server().rules());
            event.getRelevantPlayers()
                    .filter(player -> player.connection.hasChannel(SyncRestrictionsPayload.TYPE))
                    .forEach(player -> {
                        PacketDistributor.sendToPlayer(player, definitions);
                        PacketDistributor.sendToPlayer(player, rules);
                    });
        });
        StageDefinitionHooks.register(bus);
        StageLockRegistries.register(modBus, bus);
        if (ModList.get().isLoaded("ftbteams")) FtbTeamsCompat.init();
        if (ModList.get().isLoaded("kubejs")) KubeJSCompat.init();
        if (ModList.get().isLoaded("curios")) CuriosCompat.init();
        modBus.addListener(AddPackFindersEvent.class, event -> event.addPackFinders(id("datapacks/examples"), PackType.SERVER_DATA,
                Component.literal("StageLock examples"), PackSource.FEATURE, false, Pack.Position.TOP));
        bus.addListener(TagsUpdatedEvent.class, event -> {
            boolean client = event.getUpdateCause() == TagsUpdatedEvent.UpdateCause.CLIENT_PACKET_RECEIVED;
            (client ? RestrictionManager.client() : RestrictionManager.server()).invalidate();
        });
        BlockOwner.register(modBus, bus);
        MenuRefresher.register(bus);
        ItemEnforcer.register(bus);
        InventoryScanner.register(bus);
        WorldEnforcer.register(bus);
    }

    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MOD_ID, path);
    }
}
