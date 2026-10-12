package dev.stagelock.compat.curios;

import dev.stagelock.api.restriction.ItemAction;
import dev.stagelock.core.enforce.InventoryScanner;
import dev.stagelock.core.enforce.Messages;
import dev.stagelock.core.enforce.Restrictions;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.TriState;
import top.theillusivec4.curios.api.CuriosApi;
import top.theillusivec4.curios.api.event.CurioCanEquipEvent;

/** Curios slots count as equipment: locked items can't be equipped there and are taken off on revoke. */
public final class CuriosCompat {

    private CuriosCompat() {
    }

    public static void init() {
        NeoForge.EVENT_BUS.addListener(CurioCanEquipEvent.class, event -> {
            if (event.getEntity() instanceof Player player && Restrictions.isBlocked(player, event.getStack(), ItemAction.EQUIP)) {
                event.setEquipResult(TriState.FALSE);
            }
        });
        InventoryScanner.addExtraScanner(CuriosCompat::scan);
    }

    private static void scan(ServerPlayer player) {
        CuriosApi.getCuriosInventory(player).ifPresent(curios -> {
            var slots = curios.getEquippedCurios();
            for (int i = 0; i < slots.getSlots(); i++) {
                ItemStack stack = slots.getStackInSlot(i);
                if (stack.isEmpty()) continue;
                var hit = Restrictions.item(player, stack, ItemAction.EQUIP);
                if (hit == null) hit = Restrictions.item(player, stack, ItemAction.HOLD);
                if (hit == null) continue;
                slots.setStackInSlot(i, ItemStack.EMPTY);
                if (!player.getInventory().add(stack)) player.drop(stack, false, true);
                Messages.itemBlocked(player, stack, ItemAction.EQUIP, hit);
            }
        });
    }
}
