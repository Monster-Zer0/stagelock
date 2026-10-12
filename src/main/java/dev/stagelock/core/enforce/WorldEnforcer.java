package dev.stagelock.core.enforce;

import dev.stagelock.api.StageLockAPI;
import dev.stagelock.api.restriction.BlockRestriction;
import dev.stagelock.api.restriction.DimensionRestriction;
import dev.stagelock.api.restriction.EntityRestriction;
import dev.stagelock.api.restriction.FluidRestriction;
import dev.stagelock.api.restriction.RestrictionTypes;
import dev.stagelock.core.config.ServerConfig;
import dev.stagelock.core.restriction.RestrictionManager;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.util.TriState;
import net.neoforged.neoforge.event.entity.EntityTravelToDimensionEvent;
import net.neoforged.neoforge.event.entity.living.FinalizeSpawnEvent;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockDropsEvent;
import net.neoforged.neoforge.event.level.BlockEvent;

import java.util.Set;

/** Enforces block, dimension and entity rules. */
public final class WorldEnforcer {

    /** Spawn reasons that are never gated: deliberate placements by players or commands. */
    private static final Set<MobSpawnType> UNGATED_SPAWNS = Set.of(
            MobSpawnType.COMMAND, MobSpawnType.SPAWN_EGG, MobSpawnType.BUCKET, MobSpawnType.DISPENSER, MobSpawnType.CONVERSION);

    private WorldEnforcer() {
    }

    public static void register(IEventBus bus) {
        bus.addListener(EventPriority.HIGH, WorldEnforcer::onBreakSpeed);
        bus.addListener(EventPriority.HIGH, WorldEnforcer::onHarvestCheck);
        bus.addListener(EventPriority.HIGH, WorldEnforcer::onUseFluidContainer);
        bus.addListener(EventPriority.HIGH, WorldEnforcer::onUseFluidContainerOnBlock);
        bus.addListener(EventPriority.HIGH, WorldEnforcer::onBreak);
        bus.addListener(EventPriority.HIGH, WorldEnforcer::onDrops);
        bus.addListener(EventPriority.HIGH, WorldEnforcer::onRightClickBlock);
        bus.addListener(EventPriority.HIGH, WorldEnforcer::onPlace);
        bus.addListener(EventPriority.HIGH, WorldEnforcer::onTravel);
        bus.addListener(EventPriority.HIGH, WorldEnforcer::onAttackEntity);
        bus.addListener(EventPriority.HIGH, WorldEnforcer::onInteractEntity);
        bus.addListener(EventPriority.HIGH, WorldEnforcer::onInteractEntitySpecific);
        bus.addListener(EventPriority.HIGH, WorldEnforcer::onSpawn);
        bus.addListener(WorldEnforcer::onLogin);
    }

    // ---------------------------------------------------------------- blocks

    private static void onBreakSpeed(PlayerEvent.BreakSpeed event) {
        Player player = event.getEntity();
        if (WorldRestrictions.block(player, event.getState(), BlockRestriction::blocksBreaking) != null) {
            event.setCanceled(true);
            return;
        }
        BlockState disguise = WorldRestrictions.disguise(player, event.getState());
        if (disguise == null) return;
        // Mine at the disguise's pace: swap the tool speed and the hardness the game will divide by.
        BlockPos pos = event.getPosition().orElse(BlockPos.ZERO);
        float realHardness = event.getState().getDestroySpeed(player.level(), pos);
        float fakeHardness = disguise.getDestroySpeed(player.level(), pos);
        if (realHardness <= 0 || fakeHardness < 0) return;
        var tool = player.getMainHandItem();
        float realTool = Math.max(1.0F, tool.getDestroySpeed(event.getState()));
        float fakeTool = Math.max(1.0F, tool.getDestroySpeed(disguise));
        float scale = fakeHardness == 0 ? 1000F : realHardness / fakeHardness;
        event.setNewSpeed(event.getNewSpeed() / realTool * fakeTool * scale);
    }

    private static void onHarvestCheck(PlayerEvent.HarvestCheck event) {
        BlockState disguise = WorldRestrictions.disguise(event.getEntity(), event.getTargetBlock());
        if (disguise != null) {
            event.setCanHarvest(!disguise.requiresCorrectToolForDrops() || event.getEntity().getMainHandItem().isCorrectToolForDrops(disguise));
        }
    }

    /** Fluid containers: emptying a locked fluid, or filling one from a locked fluid source. */
    private static void onUseFluidContainer(PlayerInteractEvent.RightClickItem event) {
        if (fluidBlocked(event.getEntity(), event.getItemStack())) {
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.FAIL);
        }
    }

    private static void onUseFluidContainerOnBlock(PlayerInteractEvent.RightClickBlock event) {
        if (fluidBlocked(event.getEntity(), event.getItemStack())) event.setUseItem(TriState.FALSE);
    }

    private static boolean fluidBlocked(Player player, net.minecraft.world.item.ItemStack stack) {
        var handler = net.neoforged.neoforge.fluids.FluidUtil.getFluidHandler(stack);
        if (handler.isEmpty()) return false;
        var contained = net.neoforged.neoforge.fluids.FluidUtil.getFluidContained(stack);
        if (contained.isPresent()) {
            FluidRestriction rule = WorldRestrictions.fluid(player, contained.get().getFluid(), FluidRestriction.Actions::place);
            if (rule == null) return false;
            Messages.blocked(player, rule.message(), "message.stagelock.fluid.place", contained.get().getHoverName());
            return true;
        }
        if (!(player.pick(player.blockInteractionRange(), 0, true) instanceof net.minecraft.world.phys.BlockHitResult hit)
                || hit.getType() != net.minecraft.world.phys.HitResult.Type.BLOCK) return false;
        var fluid = player.level().getFluidState(hit.getBlockPos());
        if (fluid.isEmpty()) return false;
        FluidRestriction rule = WorldRestrictions.fluid(player, fluid.getType(), FluidRestriction.Actions::pickup);
        if (rule == null) return false;
        Messages.blocked(player, rule.message(), "message.stagelock.fluid.pickup", fluid.createLegacyBlock().getBlock().getName());
        return true;
    }

    private static void onBreak(BlockEvent.BreakEvent event) {
        BlockRestriction rule = WorldRestrictions.block(event.getPlayer(), event.getState(), BlockRestriction::blocksBreaking);
        if (rule != null) {
            event.setCanceled(true);
            Messages.blocked(event.getPlayer(), rule.message(), "message.stagelock.block.break", event.getState().getBlock().getName());
        }
    }

    private static void onDrops(BlockDropsEvent event) {
        if (!(event.getBreaker() instanceof Player player)) return;
        BlockState disguise = WorldRestrictions.disguise(player, event.getState());
        if (disguise != null) {
            event.getDrops().clear();
            event.setDroppedExperience(0);
            BlockPos pos = event.getPos();
            for (var stack : net.minecraft.world.level.block.Block.getDrops(disguise, event.getLevel(), pos, null, player, event.getTool())) {
                event.getDrops().add(new net.minecraft.world.entity.item.ItemEntity(event.getLevel(), pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, stack));
            }
            return;
        }
        if (WorldRestrictions.block(player, event.getState(), r -> r.actions().drops()) != null) {
            event.getDrops().clear();
            event.setDroppedExperience(0);
        }
    }

    private static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        BlockState state = event.getLevel().getBlockState(event.getPos());
        BlockRestriction rule = WorldRestrictions.block(event.getEntity(), state, r -> r.actions().interact());
        if (rule != null) {
            event.setUseBlock(TriState.FALSE);
            Messages.blocked(event.getEntity(), rule.message(), "message.stagelock.block.interact", state.getBlock().getName());
        }
    }

    private static void onPlace(BlockEvent.EntityPlaceEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        BlockRestriction rule = WorldRestrictions.block(player, event.getPlacedBlock(), r -> r.actions().place());
        if (rule != null) {
            event.setCanceled(true);
            Messages.blocked(player, rule.message(), "message.stagelock.block.place", event.getPlacedBlock().getBlock().getName());
        }
    }

    // ---------------------------------------------------------------- dimensions

    private static void onTravel(EntityTravelToDimensionEvent event) {
        for (Entity entity : event.getEntity().getSelfAndPassengers().toList()) {
            if (!(entity instanceof Player player)) continue;
            DimensionRestriction rule = WorldRestrictions.dimension(player, event.getDimension().location());
            if (rule != null) {
                event.setCanceled(true);
                Messages.blocked(player, rule.message(), "message.stagelock.dimension", event.getDimension().location().toString());
                return;
            }
        }
    }

    /** Players who log in inside a dimension they may not be in are sent to their respawn point. */
    private static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || !ServerConfig.EVICT_ON_LOGIN.get()) return;
        if (WorldRestrictions.dimension(player, player.level().dimension().location()) == null) return;
        ServerLevel overworld = player.server.overworld();
        BlockPos spawn = overworld.getSharedSpawnPos();
        player.teleportTo(overworld, spawn.getX() + 0.5, spawn.getY(), spawn.getZ() + 0.5, player.getYRot(), player.getXRot());
    }

    // ---------------------------------------------------------------- entities

    private static void onAttackEntity(AttackEntityEvent event) {
        EntityRestriction rule = WorldRestrictions.entity(event.getEntity(), event.getTarget().getType(), EntityRestriction.Actions::attack);
        if (rule != null) {
            event.setCanceled(true);
            Messages.blocked(event.getEntity(), rule.message(), "message.stagelock.entity.attack", event.getTarget().getName());
        }
    }

    private static void onInteractEntity(PlayerInteractEvent.EntityInteract event) {
        EntityRestriction rule = WorldRestrictions.entity(event.getEntity(), event.getTarget().getType(), EntityRestriction.Actions::interact);
        if (rule != null) {
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.FAIL);
            Messages.blocked(event.getEntity(), rule.message(), "message.stagelock.entity.interact", event.getTarget().getName());
        }
    }

    private static void onInteractEntitySpecific(PlayerInteractEvent.EntityInteractSpecific event) {
        if (WorldRestrictions.entity(event.getEntity(), event.getTarget().getType(), EntityRestriction.Actions::interact) != null) {
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.FAIL);
        }
    }

    /** A gated mob only spawns if some player within range meets the requirement. */
    private static void onSpawn(FinalizeSpawnEvent event) {
        if (UNGATED_SPAWNS.contains(event.getSpawnType())) return;
        Level level = event.getLevel().getLevel();
        Vec3 pos = new Vec3(event.getX(), event.getY(), event.getZ());
        for (EntityRestriction rule : RestrictionManager.server().rules(RestrictionTypes.ENTITY).values()) {
            if (rule.actions().spawn().isEmpty() || !rule.matches(event.getEntity().getType())) continue;
            EntityRestriction.Spawn spawn = rule.actions().spawn().get();
            boolean fromSpawner = event.getSpawnType() == MobSpawnType.SPAWNER || event.getSpawnType() == MobSpawnType.TRIAL_SPAWNER;
            if (fromSpawner && spawn.allowSpawners()) continue;
            if (!anyPlayerQualifies(level, pos, spawn.range(), rule)) {
                event.setSpawnCancelled(true);
                return;
            }
        }
    }

    private static boolean anyPlayerQualifies(Level level, Vec3 pos, int range, EntityRestriction rule) {
        double rangeSq = (double) range * range;
        for (Player player : level.players()) {
            if (player.isSpectator() || player.distanceToSqr(pos) > rangeSq) continue;
            if (rule.requires().test(StageLockAPI.getStages(player))) return true;
        }
        return false;
    }
}
