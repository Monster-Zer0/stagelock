package dev.stagelock.gametest;

import dev.stagelock.StageLock;
import dev.stagelock.api.StageLockAPI;
import dev.stagelock.api.StageDefinition;
import dev.stagelock.api.StageRequirement;
import dev.stagelock.api.restriction.BlockRestriction;
import dev.stagelock.api.restriction.DimensionRestriction;
import dev.stagelock.api.restriction.EntityRestriction;
import dev.stagelock.api.restriction.RegistryMatcher;
import dev.stagelock.api.restriction.Restriction;
import dev.stagelock.core.definition.StageDefinitions;
import dev.stagelock.core.loot.HasStageCondition;
import dev.stagelock.core.loot.StagesPredicate;
import dev.stagelock.core.restriction.RestrictionManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.BeforeBatch;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.neoforged.neoforge.common.CommonHooks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@GameTestHolder(StageLock.MOD_ID)
@PrefixGameTestTemplate(false)
public final class WorldGameTests {

    private static final String EMPTY = "empty";
    private static final String BATCH = "stagelock_world";

    @BeforeBatch(batch = BATCH)
    public static void installRules(ServerLevel level) {
        Map<ResourceLocation, Restriction> rules = new LinkedHashMap<>();
        rules.put(StageLock.id("test/gold_block"), new BlockRestriction(StageRequirement.of("test:mining"),
                List.of(new RegistryMatcher.Id<>(ResourceLocation.parse("minecraft:gold_block"))), List.of(),
                BlockRestriction.Actions.DEFAULT, Optional.empty(), Optional.empty()));
        rules.put(StageLock.id("test/nether"), new DimensionRestriction(StageRequirement.of("test:nether"),
                List.of(Level.NETHER.location()), Optional.empty()));
        rules.put(StageLock.id("test/pig"), new EntityRestriction(StageRequirement.of("test:hunting"),
                List.of(RegistryMatcher.parse(Registries.ENTITY_TYPE, "minecraft:pig").getOrThrow()),
                EntityRestriction.Actions.DEFAULT, Optional.empty()));
        TestPlayers.addRules(rules);
    }

    @GameTest(template = EMPTY, batch = BATCH)
    public static void blockBreakingLockedUntilUnlocked(GameTestHelper helper) {
        ServerPlayer player = TestPlayers.create(helper, GameType.SURVIVAL);
        BlockPos pos = new BlockPos(1, 1, 1);
        helper.setBlock(pos, Blocks.GOLD_BLOCK);
        helper.assertFalse(player.gameMode.destroyBlock(helper.absolutePos(pos)), "broke a locked block");
        helper.assertBlockPresent(Blocks.GOLD_BLOCK, pos);
        StageLockAPI.grant(player, "test:mining");
        helper.assertTrue(player.gameMode.destroyBlock(helper.absolutePos(pos)), "could not break after unlock");
        TestPlayers.remove(player);
        helper.succeed();
    }

    @GameTest(template = EMPTY, batch = BATCH)
    public static void dimensionTravelLockedUntilUnlocked(GameTestHelper helper) {
        ServerPlayer player = TestPlayers.create(helper, GameType.SURVIVAL);
        helper.assertFalse(CommonHooks.onTravelToDimension(player, Level.NETHER), "entered a locked dimension");
        helper.assertTrue(CommonHooks.onTravelToDimension(player, Level.OVERWORLD), "unrelated dimension was blocked");
        StageLockAPI.grant(player, "test:nether");
        helper.assertTrue(CommonHooks.onTravelToDimension(player, Level.NETHER), "still blocked after unlock");
        TestPlayers.remove(player);
        helper.succeed();
    }

    @GameTest(template = EMPTY, batch = BATCH)
    public static void attackingLockedEntityIsCancelled(GameTestHelper helper) {
        ServerPlayer player = TestPlayers.create(helper, GameType.SURVIVAL);
        Pig pig = helper.spawn(EntityType.PIG, new BlockPos(1, 1, 1));
        float health = pig.getHealth();
        player.attack(pig);
        helper.assertTrue(pig.getHealth() == health, "locked entity took damage");
        StageLockAPI.grant(player, "test:hunting");
        player.resetAttackStrengthTicker();
        player.attack(pig);
        helper.assertTrue(pig.getHealth() < health, "entity not damaged after unlock");
        TestPlayers.remove(player);
        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void lootConditionAndPredicate(GameTestHelper helper) {
        ServerPlayer player = TestPlayers.create(helper, GameType.SURVIVAL);
        var condition = new HasStageCondition(StageRequirement.of("test:loot"), LootContext.EntityTarget.THIS);
        var predicate = new StagesPredicate(StageRequirement.of("test:loot"));
        var params = new LootParams.Builder(helper.getLevel())
                .withParameter(LootContextParams.THIS_ENTITY, player)
                .withParameter(LootContextParams.ORIGIN, player.position())
                .create(LootContextParamSets.SELECTOR);
        LootContext context = new LootContext.Builder(params).create(Optional.empty());

        helper.assertFalse(condition.test(context), "loot condition passed without the stage");
        helper.assertFalse(predicate.matches(player, helper.getLevel(), player.position()), "predicate passed without the stage");
        StageLockAPI.grant(player, "test:loot");
        helper.assertTrue(condition.test(context), "loot condition failed with the stage");
        helper.assertTrue(predicate.matches(player, helper.getLevel(), player.position()), "predicate failed with the stage");
        TestPlayers.remove(player);
        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void grantingAStageGrantsItsPrerequisites(GameTestHelper helper) {
        ServerPlayer player = TestPlayers.create(helper, GameType.SURVIVAL);
        var previous = StageDefinitions.server().all();
        StageDefinition stone = StageDefinition.simple(Component.literal("Stone Age"));
        StageDefinition bronze = new StageDefinition(Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(),
                List.of("test:stone_age"), StageDefinition.Unlock.NONE, StageDefinition.OnGrant.DEFAULT);
        StageDefinition iron = new StageDefinition(Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(),
                List.of("test:bronze_age"), StageDefinition.Unlock.NONE, StageDefinition.OnGrant.DEFAULT);
        StageDefinitions.server().set(Map.of("test:stone_age", stone, "test:bronze_age", bronze, "test:iron_age", iron));
        try {
            StageLockAPI.grant(player, "test:iron_age");
            helper.assertTrue(StageLockAPI.hasAll(player, "test:stone_age", "test:bronze_age", "test:iron_age"),
                    "prerequisites not granted: " + StageLockAPI.getStages(player));
        } finally {
            StageDefinitions.server().set(previous);
            TestPlayers.remove(player);
        }
        helper.succeed();
    }
}
