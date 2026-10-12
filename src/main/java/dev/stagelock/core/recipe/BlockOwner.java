package dev.stagelock.core.recipe;

import dev.stagelock.StageLock;
import dev.stagelock.api.StageSet;
import dev.stagelock.core.config.ServerConfig;
import dev.stagelock.core.stage.StageManager;
import net.minecraft.core.UUIDUtil;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;
import java.util.function.Supplier;

/**
 * Remembers which player placed a block entity, so machines that craft without a player (furnaces,
 * campfires, crafters, modded machines) can be gated by their owner's stages, even while offline.
 */
public final class BlockOwner {

    public static final DeferredRegister<AttachmentType<?>> ATTACHMENTS =
            DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, StageLock.MOD_ID);

    private static final UUID NONE = new UUID(0, 0);

    public static final Supplier<AttachmentType<UUID>> OWNER = ATTACHMENTS.register("owner",
            () -> AttachmentType.builder(() -> NONE).serialize(UUIDUtil.CODEC).build());

    private BlockOwner() {
    }

    public static void register(IEventBus modBus, IEventBus gameBus) {
        ATTACHMENTS.register(modBus);
        gameBus.addListener(BlockOwner::onPlace);
        gameBus.addListener(BlockOwner::onRightClick);
    }

    @Nullable
    public static UUID get(BlockEntity blockEntity) {
        if (!blockEntity.hasData(OWNER)) return null;
        UUID owner = blockEntity.getData(OWNER);
        return owner.equals(NONE) ? null : owner;
    }

    public static void set(BlockEntity blockEntity, UUID owner) {
        blockEntity.setData(OWNER, owner);
        blockEntity.setChanged();
    }

    /** Stages to gate this machine's recipes with, or {@code null} for no gating. */
    @Nullable
    public static StageSet stagesFor(BlockEntity blockEntity) {
        if (blockEntity.getLevel() == null || blockEntity.getLevel().isClientSide()) return null;
        return switch (ServerConfig.AUTOMATION_POLICY.get()) {
            case ALLOW -> null;
            case DENY -> StageSet.EMPTY;
            case OWNER -> {
                UUID owner = get(blockEntity);
                StageManager manager = StageManager.getOrNull();
                if (owner == null || manager == null) {
                    yield ServerConfig.UNOWNED_POLICY.get() == ServerConfig.Policy.DENY ? StageSet.EMPTY : null;
                }
                yield manager.stages(owner);
            }
        };
    }

    private static void onPlace(BlockEvent.EntityPlaceEvent event) {
        if (!(event.getEntity() instanceof Player player) || player instanceof FakePlayer || event.getLevel().isClientSide()) return;
        BlockEntity blockEntity = event.getLevel().getBlockEntity(event.getPos());
        if (blockEntity != null) set(blockEntity, player.getUUID());
    }

    /** Claims machines placed before this mod was installed the first time someone uses them. */
    private static void onRightClick(PlayerInteractEvent.RightClickBlock event) {
        if (event.getLevel().isClientSide() || event.getEntity() instanceof FakePlayer) return;
        BlockEntity blockEntity = event.getLevel().getBlockEntity(event.getPos());
        if (blockEntity != null && get(blockEntity) == null) set(blockEntity, event.getEntity().getUUID());
    }
}
