package dev.stagelock.core;

import com.mojang.serialization.MapCodec;
import dev.stagelock.StageLock;
import dev.stagelock.api.event.StagesChangedEvent;
import dev.stagelock.core.loot.HasStageCondition;
import dev.stagelock.core.loot.StagesPredicate;
import dev.stagelock.core.loot.StagesTrigger;
import dev.stagelock.core.stage.StageManager;
import net.minecraft.advancements.CriterionTrigger;
import net.minecraft.advancements.critereon.EntitySubPredicate;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.storage.loot.predicates.LootItemConditionType;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Vanilla datapack hooks: a loot condition, an advancement trigger and an entity predicate. */
public final class StageLockRegistries {

    private static final DeferredRegister<LootItemConditionType> LOOT_CONDITIONS =
            DeferredRegister.create(Registries.LOOT_CONDITION_TYPE, StageLock.MOD_ID);
    private static final DeferredRegister<CriterionTrigger<?>> TRIGGERS =
            DeferredRegister.create(Registries.TRIGGER_TYPE, StageLock.MOD_ID);
    private static final DeferredRegister<MapCodec<? extends EntitySubPredicate>> ENTITY_PREDICATES =
            DeferredRegister.create(Registries.ENTITY_SUB_PREDICATE_TYPE, StageLock.MOD_ID);

    /** {@code {"condition": "stagelock:has_stage", "requires": ..., "entity": "this"}} */
    public static final DeferredHolder<LootItemConditionType, LootItemConditionType> HAS_STAGE =
            LOOT_CONDITIONS.register("has_stage", () -> new LootItemConditionType(HasStageCondition.MAP_CODEC));

    /** {@code {"trigger": "stagelock:has_stage", "conditions": {"requires": ...}}} */
    public static final DeferredHolder<CriterionTrigger<?>, StagesTrigger> STAGES_TRIGGER =
            TRIGGERS.register("has_stage", StagesTrigger::new);

    /** {@code "type_specific": {"type": "stagelock:stages", "requires": ...}} */
    public static final DeferredHolder<MapCodec<? extends EntitySubPredicate>, MapCodec<StagesPredicate>> STAGES_PREDICATE =
            ENTITY_PREDICATES.register("stages", () -> StagesPredicate.MAP_CODEC);

    private StageLockRegistries() {
    }

    public static void register(IEventBus modBus, IEventBus gameBus) {
        LOOT_CONDITIONS.register(modBus);
        TRIGGERS.register(modBus);
        ENTITY_PREDICATES.register(modBus);
        gameBus.addListener(StagesChangedEvent.class, event -> {
            if (event.getEntity() instanceof ServerPlayer player) STAGES_TRIGGER.get().trigger(player, event.getStages());
        });
        gameBus.addListener(EventPriority.LOW, PlayerEvent.PlayerLoggedInEvent.class, event -> {
            if (event.getEntity() instanceof ServerPlayer player && StageManager.getOrNull() != null) {
                STAGES_TRIGGER.get().trigger(player, StageManager.get().stages(player));
            }
        });
    }
}
