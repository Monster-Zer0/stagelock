package dev.stagelock.client;

import dev.stagelock.core.config.ClientConfig;
import dev.stagelock.core.enforce.Restrictions;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * Supplies the hidden name for restricted items. Called from the {@code ItemStack#getHoverName} mixin,
 * so one hook covers tooltips, the hotbar name popup, item frames, Jade and anything else that asks
 * for the item's name on the client.
 */
public final class HiddenNames {

    private static boolean resolving;

    private HiddenNames() {
    }

    @Nullable
    public static Component get(ItemStack stack) {
        if (resolving || stack.isEmpty() || !ClientConfig.SPEC.isLoaded() || !ClientConfig.HIDE_NAMES.get()) return null;
        Minecraft minecraft = Minecraft.getInstance();
        // The integrated server runs in the same JVM; only rewrite names on the render thread.
        if (!minecraft.isSameThread()) return null;
        Player player = minecraft.player;
        if (player == null) return null;
        resolving = true;
        try {
            var hit = Restrictions.item(player, stack, null);
            return hit != null && hit.rule().hideName() ? hit.rule().displayName() : null;
        } finally {
            resolving = false;
        }
    }
}
