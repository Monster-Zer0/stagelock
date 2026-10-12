package dev.stagelock.client;

import dev.stagelock.api.restriction.ItemRestriction;
import dev.stagelock.core.config.ClientConfig;
import dev.stagelock.core.enforce.Messages;
import dev.stagelock.core.enforce.Restrictions;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;

import java.util.List;

public final class TooltipHandler {

    private TooltipHandler() {
    }

    public static void onTooltip(ItemTooltipEvent event) {
        Player player = Minecraft.getInstance().player;
        if (player == null) return;
        var hit = Restrictions.item(player, event.getItemStack(), null);
        if (hit == null) return;
        ItemRestriction rule = hit.rule();
        if (rule.tooltip() == ItemRestriction.TooltipMode.NONE) return;

        List<Component> lines = event.getToolTip();
        if (rule.tooltip() == ItemRestriction.TooltipMode.REPLACE && lines.size() > 1) {
            lines.subList(1, lines.size()).clear();
        }
        lines.add(Component.translatable("tooltip.stagelock.locked").withStyle(ChatFormatting.DARK_RED));
        if (ClientConfig.SHOW_REQUIREMENTS.get()) {
            lines.add(Messages.requirement(rule.requires(), player));
        }
        if (event.getFlags().isAdvanced()) {
            lines.add(Component.literal("Rule: " + hit.id()).withStyle(ChatFormatting.DARK_GRAY));
        }
    }
}
