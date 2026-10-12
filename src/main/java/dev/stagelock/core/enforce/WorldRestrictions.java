package dev.stagelock.core.enforce;

import dev.stagelock.api.StageLockAPI;
import dev.stagelock.api.StageSet;
import dev.stagelock.api.restriction.BlockRestriction;
import dev.stagelock.api.restriction.DimensionRestriction;
import dev.stagelock.api.restriction.EntityRestriction;
import dev.stagelock.api.restriction.FluidRestriction;
import dev.stagelock.api.restriction.RestrictionTypes;
import dev.stagelock.core.restriction.RestrictionManager;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.function.Predicate;

/** Lookups for block, dimension and entity rules. These rule sets are small, so a linear scan is fine. */
public final class WorldRestrictions {

    private WorldRestrictions() {
    }

    @Nullable
    public static BlockRestriction block(Player player, BlockState state, Predicate<BlockRestriction> action) {
        if (Restrictions.isExempt(player)) return null;
        return block(RestrictionManager.of(player), StageLockAPI.getStages(player), state, action);
    }

    @Nullable
    public static BlockRestriction block(RestrictionManager manager, StageSet stages, BlockState state, Predicate<BlockRestriction> action) {
        for (BlockRestriction rule : manager.rules(RestrictionTypes.BLOCK).values()) {
            if (action.test(rule) && !rule.requires().test(stages) && rule.matches(state)) return rule;
        }
        return null;
    }

    @Nullable
    public static FluidRestriction fluid(Player player, net.minecraft.world.level.material.Fluid fluid, Predicate<FluidRestriction.Actions> action) {
        if (Restrictions.isExempt(player)) return null;
        StageSet stages = StageLockAPI.getStages(player);
        for (FluidRestriction rule : RestrictionManager.of(player).rules(RestrictionTypes.FLUID).values()) {
            if (action.test(rule.actions()) && !rule.requires().test(stages) && rule.matches(fluid)) return rule;
        }
        return null;
    }

    /** The block a locked player should see/mine instead, or {@code null}. */
    @Nullable
    public static BlockState disguise(Player player, BlockState state) {
        BlockRestriction rule = block(player, state, r -> r.disguiseAs().isPresent());
        return rule == null ? null : rule.disguiseAs().get().defaultBlockState();
    }

    @Nullable
    public static DimensionRestriction dimension(Player player, ResourceLocation dimension) {
        if (Restrictions.isExempt(player)) return null;
        StageSet stages = StageLockAPI.getStages(player);
        for (DimensionRestriction rule : RestrictionManager.of(player).rules(RestrictionTypes.DIMENSION).values()) {
            if (rule.dimensions().contains(dimension) && !rule.requires().test(stages)) return rule;
        }
        return null;
    }

    @Nullable
    public static EntityRestriction entity(Player player, EntityType<?> type, Predicate<EntityRestriction.Actions> action) {
        if (Restrictions.isExempt(player)) return null;
        StageSet stages = StageLockAPI.getStages(player);
        for (EntityRestriction rule : RestrictionManager.of(player).rules(RestrictionTypes.ENTITY).values()) {
            if (action.test(rule.actions()) && !rule.requires().test(stages) && rule.matches(type)) return rule;
        }
        return null;
    }
}
