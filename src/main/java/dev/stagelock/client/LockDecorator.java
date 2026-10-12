package dev.stagelock.client;

import dev.stagelock.core.config.ClientConfig;
import dev.stagelock.core.enforce.Restrictions;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.client.IItemDecorator;
import net.neoforged.neoforge.client.event.RegisterItemDecorationsEvent;

/**
 * Draws a small padlock on locked items wherever item decorations render: inventories, containers,
 * and recipe viewers. Items with {@code viewer: show} (or {@code lock}) stay visible in JEI/REI/EMI
 * with this icon instead of disappearing.
 */
public final class LockDecorator implements IItemDecorator {

    private static final LockDecorator INSTANCE = new LockDecorator();
    private static final int BODY = 0xFFE0B040;
    private static final int SHACKLE = 0xFFB0B0B0;
    private static final int OUTLINE = 0xFF202020;

    public static void register(RegisterItemDecorationsEvent event) {
        for (var item : BuiltInRegistries.ITEM) event.register(item, INSTANCE);
    }

    @Override
    public boolean render(GuiGraphics graphics, Font font, ItemStack stack, int x, int y) {
        if (!ClientConfig.SPEC.isLoaded() || !ClientConfig.SHOW_LOCK_ICON.get()) return false;
        var player = Minecraft.getInstance().player;
        if (player == null || Restrictions.item(player, stack, null) == null) return false;
        graphics.pose().pushPose();
        graphics.pose().translate(0, 0, 200);
        int lx = x + 9;
        int ly = y;
        // Shackle
        graphics.fill(lx + 1, ly, lx + 6, ly + 1, OUTLINE);
        graphics.fill(lx + 1, ly + 1, lx + 2, ly + 4, SHACKLE);
        graphics.fill(lx + 5, ly + 1, lx + 6, ly + 4, SHACKLE);
        graphics.fill(lx + 2, ly + 1, lx + 5, ly + 2, SHACKLE);
        // Body with keyhole
        graphics.fill(lx, ly + 3, lx + 7, ly + 8, OUTLINE);
        graphics.fill(lx + 1, ly + 4, lx + 6, ly + 7, BODY);
        graphics.fill(lx + 3, ly + 5, lx + 4, ly + 7, OUTLINE);
        graphics.pose().popPose();
        return false;
    }
}
