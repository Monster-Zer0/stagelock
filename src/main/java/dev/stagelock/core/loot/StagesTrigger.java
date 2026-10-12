package dev.stagelock.core.loot;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.stagelock.api.StageRequirement;
import dev.stagelock.api.StageSet;
import net.minecraft.advancements.critereon.ContextAwarePredicate;
import net.minecraft.advancements.critereon.EntityPredicate;
import net.minecraft.advancements.critereon.SimpleCriterionTrigger;
import net.minecraft.server.level.ServerPlayer;

import java.util.Optional;

/**
 * Advancement trigger {@code stagelock:has_stage}: completes once the player's stages satisfy
 * {@code requires}. Checked on login and whenever the player's stages change.
 */
public final class StagesTrigger extends SimpleCriterionTrigger<StagesTrigger.Instance> {

    @Override
    public Codec<Instance> codec() {
        return Instance.CODEC;
    }

    public void trigger(ServerPlayer player, StageSet stages) {
        trigger(player, instance -> instance.requires().test(stages));
    }

    public record Instance(Optional<ContextAwarePredicate> player, StageRequirement requires) implements SimpleCriterionTrigger.SimpleInstance {
        public static final Codec<Instance> CODEC = RecordCodecBuilder.create(i -> i.group(
                EntityPredicate.ADVANCEMENT_CODEC.optionalFieldOf("player").forGetter(Instance::player),
                StageRequirement.CODEC.fieldOf("requires").forGetter(Instance::requires)
        ).apply(i, Instance::new));
    }
}
