package dev.stagelock.core.recipe;

import dev.stagelock.api.event.StagesChangedEvent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.inventory.SmithingMenu;
import net.neoforged.bus.api.IEventBus;

/** Re-evaluates the open crafting output when a player's stages change, so unlocking shows the result at once. */
public final class MenuRefresher {

    private MenuRefresher() {
    }

    public static void register(IEventBus bus) {
        bus.addListener(MenuRefresher::onStagesChanged);
    }

    private static void onStagesChanged(StagesChangedEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        refresh(player.containerMenu);
        if (player.containerMenu != player.inventoryMenu) refresh(player.inventoryMenu);
    }

    private static void refresh(AbstractContainerMenu menu) {
        // Both implementations ignore the container argument and recompute the whole grid.
        if (menu instanceof CraftingMenu || menu instanceof InventoryMenu) {
            menu.slotsChanged(null);
        } else if (menu instanceof SmithingMenu smithing) {
            smithing.createResult();
        }
    }
}
