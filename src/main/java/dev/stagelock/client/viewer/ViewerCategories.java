package dev.stagelock.client.viewer;

import com.mojang.brigadier.Command;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;
import net.neoforged.neoforge.common.NeoForge;

import java.util.Collection;
import java.util.Map;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import java.util.function.ToIntFunction;

/**
 * Lists the category ids of the loaded recipe viewers, so pack makers can find what to put in a
 * {@code stagelock:viewer_category} rule. Client command: {@code /stagelock_categories}.
 */
public final class ViewerCategories {

    private static final Map<String, Source> SOURCES = new ConcurrentHashMap<>();

    /** {@code hiddenEntries} answers how many entries of a category StageLock hides, or -1 if the viewer can't tell. */
    private record Source(Supplier<Collection<ResourceLocation>> categories, ToIntFunction<ResourceLocation> hiddenEntries) {
    }

    private ViewerCategories() {
    }

    /** Viewer plugins register where their current category ids come from. */
    public static void register(String viewer, Supplier<Collection<ResourceLocation>> categories) {
        register(viewer, categories, id -> -1);
    }

    public static void register(String viewer, Supplier<Collection<ResourceLocation>> categories, ToIntFunction<ResourceLocation> hiddenEntries) {
        SOURCES.put(viewer, new Source(categories, hiddenEntries));
    }

    public static void init() {
        NeoForge.EVENT_BUS.addListener(RegisterClientCommandsEvent.class, e -> e.getDispatcher().register(
                Commands.literal("stagelock_categories").executes(ctx -> {
                    var source = ctx.getSource();
                    if (SOURCES.isEmpty()) {
                        source.sendSystemMessage(Component.translatable("stagelock.categories.none"));
                        return 0;
                    }
                    SOURCES.forEach((viewer, viewerSource) -> {
                        source.sendSystemMessage(Component.literal(viewer).withStyle(ChatFormatting.GOLD));
                        for (ResourceLocation id : new TreeSet<>(viewerSource.categories().get())) {
                            boolean hidden = ViewerState.isCategoryHidden(id);
                            int entries = viewerSource.hiddenEntries().applyAsInt(id);
                            Component line = Component.literal("  " + id);
                            if (entries > 0) line = Component.translatable("stagelock.categories.entries", line, entries);
                            source.sendSystemMessage(line.copy().withStyle(style -> style
                                    .withColor(hidden ? ChatFormatting.RED : ChatFormatting.GRAY)
                                    .withClickEvent(new ClickEvent(ClickEvent.Action.COPY_TO_CLIPBOARD, id.toString()))
                                    .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                                            Component.translatable(hidden ? "stagelock.categories.hidden" : "stagelock.categories.copy")))));
                        }
                    });
                    return Command.SINGLE_SUCCESS;
                })));
    }
}
