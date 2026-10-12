package dev.stagelock.compat.jade;

import dev.stagelock.StageLock;
import dev.stagelock.api.restriction.BlockRestriction;
import dev.stagelock.core.enforce.Messages;
import dev.stagelock.core.enforce.WorldRestrictions;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IBlockComponentProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.IWailaClientRegistration;
import snownee.jade.api.IWailaPlugin;
import snownee.jade.api.WailaPlugin;
import snownee.jade.api.config.IPluginConfig;

/**
 * Jade: disguised blocks are reported as their disguise (no name leak), and locked blocks show what
 * they require. Item names are already handled by the hidden-name mixin.
 */
@WailaPlugin
public final class StageLockJadePlugin implements IWailaPlugin {

    @Override
    public void registerClient(IWailaClientRegistration registration) {
        registration.registerBlockComponent(LockedBlock.INSTANCE, Block.class);
        registration.addRayTraceCallback((hit, accessor, original) -> {
            if (accessor instanceof BlockAccessor block) {
                BlockState disguise = WorldRestrictions.disguise(block.getPlayer(), block.getBlockState());
                if (disguise != null) return registration.blockAccessor().from(block).blockState(disguise).build();
            }
            return accessor;
        });
    }

    enum LockedBlock implements IBlockComponentProvider {
        INSTANCE;

        private static final ResourceLocation UID = StageLock.id("locked_block");

        @Override
        public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
            BlockRestriction rule = WorldRestrictions.block(accessor.getPlayer(), accessor.getBlockState(),
                    r -> r.disguiseAs().isEmpty() && (r.blocksBreaking() || r.actions().interact()));
            if (rule == null) return;
            tooltip.add(Component.translatable("tooltip.stagelock.locked").withStyle(ChatFormatting.DARK_RED));
            tooltip.add(Messages.requirement(rule.requires(), accessor.getPlayer()));
        }

        @Override
        public ResourceLocation getUid() {
            return UID;
        }
    }
}
