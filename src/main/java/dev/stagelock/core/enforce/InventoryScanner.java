package dev.stagelock.core.enforce;

import dev.stagelock.api.StageLockAPI;
import dev.stagelock.api.restriction.ItemAction;
import dev.stagelock.api.restriction.ItemRestriction;
import dev.stagelock.core.restriction.RestrictionManager;
import net.minecraft.core.NonNullList;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.entity.living.LivingEquipmentChangeEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Removes items a player may not hold, and takes off armor they may not wear.
 * <p>
 * Unlike ItemStages, this does not scan every slot every tick: a player's inventory is only scanned
 * when its change counter, the player's stages, or the active rules changed since the last scan.
 */
public final class InventoryScanner {

    private record ScanState(int inventoryChanges, long stagesVersion, int rulesGeneration) {
    }

    private static final Map<UUID, ScanState> STATES = new ConcurrentHashMap<>();
    private static final java.util.List<java.util.function.Consumer<ServerPlayer>> EXTRA = new java.util.concurrent.CopyOnWriteArrayList<>();

    private InventoryScanner() {
    }

    public static void register(IEventBus bus) {
        bus.addListener(InventoryScanner::onTick);
        bus.addListener(InventoryScanner::onEquipmentChange);
        bus.addListener(PlayerEvent.PlayerLoggedOutEvent.class, e -> {
            STATES.remove(e.getEntity().getUUID());
            Messages.forget(e.getEntity().getUUID());
        });
    }

    /** Lets integrations scan extra slots (e.g. Curios) whenever the inventory is scanned. */
    public static void addExtraScanner(java.util.function.Consumer<ServerPlayer> scanner) {
        EXTRA.add(scanner);
    }

    /** Forces a rescan on the player's next tick. */
    public static void markDirty(UUID player) {
        STATES.remove(player);
    }

    private static void onEquipmentChange(LivingEquipmentChangeEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && event.getSlot().isArmor()) markDirty(player.getUUID());
    }

    private static void onTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        ScanState now = new ScanState(player.getInventory().getTimesChanged(),
                StageLockAPI.getStages(player).version(), RestrictionManager.server().generation());
        if (now.equals(STATES.get(player.getUUID()))) return;
        scan(player);
        // Record the counter *after* our own removals so they don't trigger another scan.
        STATES.put(player.getUUID(), new ScanState(player.getInventory().getTimesChanged(), now.stagesVersion(), now.rulesGeneration()));
    }

    static void scan(ServerPlayer player) {
        if (Restrictions.isExempt(player)) return;
        Inventory inventory = player.getInventory();
        EXTRA.forEach(scanner -> scanner.accept(player));
        scanList(player, inventory.items);
        scanList(player, inventory.offhand);
        for (int i = 0; i < inventory.armor.size(); i++) {
            ItemStack stack = inventory.armor.get(i);
            if (stack.isEmpty()) continue;
            if (removeIfHeldIllegally(player, inventory.armor, i)) continue;
            var hit = Restrictions.item(player, stack, ItemAction.EQUIP);
            if (hit != null) {
                inventory.armor.set(i, ItemStack.EMPTY);
                if (!inventory.add(stack)) player.drop(stack, false, true);
                inventory.setChanged();
                Messages.itemBlocked(player, stack, ItemAction.EQUIP, hit);
            }
        }
    }

    private static void scanList(ServerPlayer player, NonNullList<ItemStack> slots) {
        for (int i = 0; i < slots.size(); i++) {
            if (!slots.get(i).isEmpty()) removeIfHeldIllegally(player, slots, i);
        }
    }

    private static boolean removeIfHeldIllegally(ServerPlayer player, NonNullList<ItemStack> slots, int slot) {
        ItemStack stack = slots.get(slot);
        var hit = Restrictions.item(player, stack, ItemAction.HOLD);
        if (hit == null) return false;
        slots.set(slot, ItemStack.EMPTY);
        player.getInventory().setChanged();
        if (hit.rule().actions().hold() == ItemRestriction.HoldMode.DROP) {
            player.drop(stack, false, true);
        }
        Messages.itemBlocked(player, stack, ItemAction.HOLD, hit);
        return true;
    }
}
