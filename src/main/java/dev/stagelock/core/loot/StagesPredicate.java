package dev.stagelock.core.loot;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.stagelock.api.StageLockAPI;
import dev.stagelock.api.StageRequirement;
import net.minecraft.advancements.critereon.EntitySubPredicate;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/** Entity sub-predicate {@code stagelock:stages}, usable anywhere vanilla accepts an entity predicate. */
public record StagesPredicate(StageRequirement requires) implements EntitySubPredicate {

    public static final MapCodec<StagesPredicate> MAP_CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            StageRequirement.CODEC.fieldOf("requires").forGetter(StagesPredicate::requires)
    ).apply(i, StagesPredicate::new));

    @Override
    public MapCodec<? extends EntitySubPredicate> codec() {
        return MAP_CODEC;
    }

    @Override
    public boolean matches(Entity entity, ServerLevel level, @Nullable Vec3 position) {
        return entity instanceof Player player && requires.test(StageLockAPI.getStages(player));
    }
}
