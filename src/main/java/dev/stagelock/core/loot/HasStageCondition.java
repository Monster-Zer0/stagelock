package dev.stagelock.core.loot;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.stagelock.api.StageLockAPI;
import dev.stagelock.api.StageRequirement;
import dev.stagelock.core.StageLockRegistries;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.parameters.LootContextParam;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import net.minecraft.world.level.storage.loot.predicates.LootItemConditionType;

import java.util.Set;

/**
 * Loot condition {@code stagelock:has_stage}: passes when the chosen entity is a player meeting the
 * requirement. Use it to gate chest loot, mob drops or block drops.
 */
public record HasStageCondition(StageRequirement requires, LootContext.EntityTarget entity) implements LootItemCondition {

    public static final MapCodec<HasStageCondition> MAP_CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            StageRequirement.CODEC.fieldOf("requires").forGetter(HasStageCondition::requires),
            LootContext.EntityTarget.CODEC.optionalFieldOf("entity", LootContext.EntityTarget.THIS).forGetter(HasStageCondition::entity)
    ).apply(i, HasStageCondition::new));

    @Override
    public LootItemConditionType getType() {
        return StageLockRegistries.HAS_STAGE.get();
    }

    @Override
    public Set<LootContextParam<?>> getReferencedContextParams() {
        return Set.of(entity.getParam());
    }

    @Override
    public boolean test(LootContext context) {
        Entity target = context.getParamOrNull(entity.getParam());
        return target instanceof Player player && requires.test(StageLockAPI.getStages(player));
    }
}
