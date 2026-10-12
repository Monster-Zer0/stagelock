package dev.stagelock.core.enforce;

import dev.stagelock.api.restriction.ItemAction;
import dev.stagelock.api.restriction.ItemRestriction;
import dev.stagelock.core.config.ServerConfig;
import dev.stagelock.core.restriction.RestrictionManager;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.util.TriState;
import net.neoforged.neoforge.event.entity.living.LivingEntityUseItemEvent;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;
import net.neoforged.neoforge.event.entity.player.ItemEntityPickupEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import org.jetbrains.annotations.Nullable;

/**
 * Blocks item interactions. Interaction events fire on both sides; cancelling on the client too keeps
 * the client from predicting an action the server will refuse (no ghost blocks or arm swings).
 * Feedback messages are only sent from the server.
 */
public final class ItemEnforcer {

    private ItemEnforcer() {
    }

    public static void register(IEventBus bus) {
        bus.addListener(EventPriority.HIGH, ItemEnforcer::onPickup);
        bus.addListener(EventPriority.HIGH, ItemEnforcer::onRightClickItem);
        bus.addListener(EventPriority.HIGH, ItemEnforcer::onRightClickBlock);
        bus.addListener(EventPriority.HIGH, ItemEnforcer::onEntityInteract);
        bus.addListener(EventPriority.HIGH, ItemEnforcer::onEntityInteractSpecific);
        bus.addListener(EventPriority.HIGH, ItemEnforcer::onUseStart);
        bus.addListener(EventPriority.HIGH, ItemEnforcer::onAttack);
        bus.addListener(EventPriority.HIGH, ItemEnforcer::onBreakSpeed);
        bus.addListener(EventPriority.HIGH, ItemEnforcer::onHarvestCheck);
        bus.addListener(EventPriority.HIGH, ItemEnforcer::onPlace);
    }

    @Nullable
    private static RestrictionManager.Entry<ItemRestriction> check(Player player, ItemStack stack, ItemAction action) {
        var hit = Restrictions.item(player, stack, action);
        if (hit != null) Messages.itemBlocked(player, stack, action, hit);
        return hit;
    }

    private static void onPickup(ItemEntityPickupEvent.Pre event) {
        if (check(event.getPlayer(), event.getItemEntity().getItem(), ItemAction.PICKUP) != null) {
            event.setCanPickup(TriState.FALSE);
        }
    }

    private static void onRightClickItem(PlayerInteractEvent.RightClickItem event) {
        if (check(event.getEntity(), event.getItemStack(), ItemAction.USE) != null) {
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.FAIL);
        }
    }

    private static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        ItemStack stack = event.getItemStack();
        ItemAction action = stack.getItem() instanceof BlockItem ? ItemAction.PLACE : ItemAction.USE;
        if (check(event.getEntity(), stack, action) != null
                || action == ItemAction.PLACE && check(event.getEntity(), stack, ItemAction.USE) != null) {
            // Still allow using the block itself (opening a chest while holding a locked item).
            event.setUseItem(TriState.FALSE);
        }
    }

    private static void onEntityInteract(PlayerInteractEvent.EntityInteract event) {
        if (check(event.getEntity(), event.getItemStack(), ItemAction.USE) != null) {
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.FAIL);
        }
    }

    private static void onEntityInteractSpecific(PlayerInteractEvent.EntityInteractSpecific event) {
        if (check(event.getEntity(), event.getItemStack(), ItemAction.USE) != null) {
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.FAIL);
        }
    }

    /** Continuous use: eating, drinking, bows, shields, tridents. */
    private static void onUseStart(LivingEntityUseItemEvent.Start event) {
        if (event.getEntity() instanceof Player player && check(player, event.getItem(), ItemAction.USE) != null) {
            event.setCanceled(true);
        }
    }

    private static void onAttack(AttackEntityEvent event) {
        Player player = event.getEntity();
        if (check(player, player.getMainHandItem(), ItemAction.ATTACK) != null) {
            event.setCanceled(true);
        }
    }

    private static void onBreakSpeed(PlayerEvent.BreakSpeed event) {
        Player player = event.getEntity();
        if (Restrictions.item(player, player.getMainHandItem(), ItemAction.MINE) == null) return;
        if (ServerConfig.RESTRICTED_TOOL_MINING.get() == ServerConfig.ToolMining.DENY) {
            event.setCanceled(true);
        } else {
            event.setNewSpeed(Math.min(event.getNewSpeed(), 1.0F));
        }
    }

    private static void onHarvestCheck(PlayerEvent.HarvestCheck event) {
        Player player = event.getEntity();
        if (event.canHarvest() && event.getTargetBlock().requiresCorrectToolForDrops()
                && Restrictions.item(player, player.getMainHandItem(), ItemAction.MINE) != null) {
            event.setCanHarvest(false);
        }
    }

    /** Backstop for placements that bypass the right-click event (e.g. other mods' placers). */
    private static void onPlace(BlockEvent.EntityPlaceEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        ItemStack stack = new ItemStack(event.getPlacedBlock().getBlock().asItem());
        if (check(player, stack, ItemAction.PLACE) != null) {
            event.setCanceled(true);
        }
    }
}
