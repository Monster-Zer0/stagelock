package dev.stagelock.client;

import dev.stagelock.api.StageSet;
import dev.stagelock.api.event.RestrictionsReloadedEvent;
import dev.stagelock.api.event.StagesChangedEvent;
import dev.stagelock.api.restriction.BlockRestriction;
import dev.stagelock.api.restriction.RestrictionTypes;
import dev.stagelock.core.enforce.Restrictions;
import dev.stagelock.core.restriction.RestrictionManager;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.TagsUpdatedEvent;
import org.jetbrains.annotations.Nullable;

import java.util.IdentityHashMap;
import java.util.Map;

/**
 * Which blocks the local player sees as something else ({@code disguise_as}). Read by the
 * {@code BlockModelShaper} mixin from chunk-building threads, so the map is swapped atomically and
 * never mutated after publication. Changing it re-renders all chunks.
 */
public final class BlockDisguises {

    private static volatile Map<Block, BlockState> disguises = Map.of();
    private static boolean dirty;
    private static boolean lastExempt;

    private BlockDisguises() {
    }

    public static void init() {
        NeoForge.EVENT_BUS.addListener(StagesChangedEvent.class, e -> {
            if (e.getEntity().level().isClientSide()) dirty = true;
        });
        NeoForge.EVENT_BUS.addListener(RestrictionsReloadedEvent.class, e -> {
            if (e.isClientSide()) dirty = true;
        });
        NeoForge.EVENT_BUS.addListener(TagsUpdatedEvent.class, e -> dirty = true);
        NeoForge.EVENT_BUS.addListener(ClientTickEvent.Post.class, e -> tick());
    }

    @Nullable
    public static BlockState get(Block block) {
        Map<Block, BlockState> current = disguises;
        return current.isEmpty() ? null : current.get(block);
    }

    private static void tick() {
        var player = Minecraft.getInstance().player;
        if (player == null) {
            disguises = Map.of();
            return;
        }
        boolean exempt = Restrictions.isExempt(player);
        if (exempt != lastExempt) {
            lastExempt = exempt;
            dirty = true;
        }
        if (!dirty) return;
        dirty = false;
        Map<Block, BlockState> next = compute(exempt ? null : ClientStageCache.get());
        if (!next.equals(disguises)) {
            disguises = next;
            Minecraft.getInstance().levelRenderer.allChanged();
        }
    }

    private static Map<Block, BlockState> compute(@Nullable StageSet stages) {
        if (stages == null) return Map.of();
        Map<Block, BlockState> out = new IdentityHashMap<>();
        for (BlockRestriction rule : RestrictionManager.client().rules(RestrictionTypes.BLOCK).values()) {
            if (rule.disguiseAs().isEmpty() || rule.requires().test(stages)) continue;
            BlockState disguise = rule.disguiseAs().get().defaultBlockState();
            for (Block block : BuiltInRegistries.BLOCK) {
                if (block != disguise.getBlock() && !out.containsKey(block) && rule.matches(block.defaultBlockState())) {
                    out.put(block, disguise);
                }
            }
        }
        return out.isEmpty() ? Map.of() : out;
    }
}
