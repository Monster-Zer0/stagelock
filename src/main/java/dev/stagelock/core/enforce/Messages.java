package dev.stagelock.core.enforce;

import dev.stagelock.api.StageLockAPI;
import dev.stagelock.api.StageRequirement;
import dev.stagelock.api.restriction.ItemAction;
import dev.stagelock.api.restriction.ItemRestriction;
import dev.stagelock.core.config.ServerConfig;
import dev.stagelock.core.definition.StageDefinitions;
import dev.stagelock.core.restriction.RestrictionManager;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Rate-limited action-bar feedback, so standing on a locked item doesn't spam the player every tick. */
public final class Messages {

    private static final Map<UUID, Long> LAST_SENT = new ConcurrentHashMap<>();

    private Messages() {
    }

    /** Sends {@code custom} if present, else the translated default, to the action bar (rate limited). */
    public static void blocked(Player player, java.util.Optional<Component> custom, String key, Object... args) {
        if (!(player instanceof ServerPlayer serverPlayer) || onCooldown(serverPlayer)) return;
        Component message = custom.orElseGet(() -> Component.translatable(key, args));
        serverPlayer.displayClientMessage(message.copy().withStyle(ChatFormatting.RED), true);
    }

    private static boolean onCooldown(ServerPlayer player) {
        long now = player.serverLevel().getGameTime();
        Long last = LAST_SENT.get(player.getUUID());
        if (last != null && now - last < ServerConfig.MESSAGE_COOLDOWN_TICKS.get()) return true;
        LAST_SENT.put(player.getUUID(), now);
        return false;
    }

    public static void itemBlocked(Player player, ItemStack stack, ItemAction action, RestrictionManager.Entry<ItemRestriction> hit) {
        if (!(player instanceof ServerPlayer serverPlayer) || onCooldown(serverPlayer)) return;

        ItemRestriction rule = hit.rule();
        Component name = rule.hideName() ? rule.displayName() : stack.getHoverName();
        Component message = rule.message().orElseGet(() -> Component.translatable(
                "message.stagelock.item." + action.name().toLowerCase(Locale.ROOT), name));
        serverPlayer.displayClientMessage(message.copy().withStyle(ChatFormatting.RED), true);
    }

    public static void forget(UUID player) {
        LAST_SENT.remove(player);
    }

    /** "Requires: a, b" with each missing stage in red. */
    public static MutableComponent requirement(StageRequirement requirement, Player player) {
        Set<String> missing = requirement.missing(StageLockAPI.getStages(player));
        MutableComponent stages = Component.empty();
        boolean first = true;
        for (String stage : missing) {
            if (!first) stages.append(Component.literal(", ").withStyle(ChatFormatting.GRAY));
            stages.append(StageDefinitions.of(player).displayName(stage).copy().withStyle(ChatFormatting.RED));
            first = false;
        }
        return Component.translatable("tooltip.stagelock.requires", stages).withStyle(ChatFormatting.GRAY);
    }
}
