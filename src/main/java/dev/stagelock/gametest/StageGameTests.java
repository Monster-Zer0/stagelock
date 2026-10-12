package dev.stagelock.gametest;

import dev.stagelock.StageLock;
import dev.stagelock.api.StageLockAPI;
import dev.stagelock.api.StageScope;
import dev.stagelock.api.event.StageEvent;
import dev.stagelock.core.stage.StageManager;
import dev.stagelock.core.stage.StageStore;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.List;
import java.util.Set;
import java.util.UUID;

@GameTestHolder(StageLock.MOD_ID)
@PrefixGameTestTemplate(false)
public final class StageGameTests {

    private static final String EMPTY = "empty";

    @GameTest(template = EMPTY)
    public static void grantAndRevoke(GameTestHelper helper) {
        ServerPlayer player = TestPlayers.create(helper, net.minecraft.world.level.GameType.CREATIVE);
        helper.assertFalse(StageLockAPI.has(player, "test:iron_age"), "fresh player has stage");

        helper.assertTrue(StageLockAPI.grant(player, "test:iron_age").equals(Set.of("test:iron_age")), "grant reported no change");
        helper.assertTrue(StageLockAPI.has(player, "test:iron_age"), "stage missing right after grant");
        helper.assertTrue(StageLockAPI.grant(player, "test:iron_age").isEmpty(), "re-grant should be a no-op");

        StageLockAPI.revoke(player, "test:iron_age");
        helper.assertFalse(StageLockAPI.has(player, "test:iron_age"), "stage present after revoke");
        helper.succeed();
    }

    /** Regression: plain player names used to NPE on servers without a profile cache (like this one). */
    @GameTest(template = EMPTY)
    public static void grantCommandAcceptsPlayerName(GameTestHelper helper) throws Exception {
        ServerPlayer player = TestPlayers.create(helper, net.minecraft.world.level.GameType.SURVIVAL);
        var server = helper.getLevel().getServer();
        var dispatcher = server.getCommands().getDispatcher();
        String name = player.getGameProfile().getName();
        dispatcher.execute("stagelock grant " + name + " test:by_name", server.createCommandSourceStack());
        helper.assertTrue(StageLockAPI.has(player, "test:by_name"), "grant by name did not apply");
        dispatcher.execute("stagelock revoke " + name + " test:by_name", server.createCommandSourceStack());
        helper.assertFalse(StageLockAPI.has(player, "test:by_name"), "revoke by name did not apply");
        try {
            dispatcher.execute("stagelock grant nobody_here test:by_name", server.createCommandSourceStack());
            helper.fail("unknown name should be rejected");
        } catch (com.mojang.brigadier.exceptions.CommandSyntaxException expected) {
            // "Unknown player", not a crash
        }
        TestPlayers.remove(player);
        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void offlineGrantIsStored(GameTestHelper helper) {
        UUID offline = UUID.randomUUID();
        StageLockAPI.grant(offline, List.of("test:offline"));
        helper.assertTrue(StageStore.get(helper.getLevel().getServer()).player(offline).contains("test:offline"), "offline grant not stored");
        helper.assertTrue(StageManager.get().stages(offline).has("test:offline"), "offline grant not effective");
        StageLockAPI.clear(offline);
        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void globalStagesApplyToEveryone(GameTestHelper helper) {
        ServerPlayer player = TestPlayers.create(helper, net.minecraft.world.level.GameType.CREATIVE);
        StageLockAPI.global().grant("test:global");
        helper.assertTrue(StageLockAPI.has(player, "test:global"), "global stage not effective for player");
        helper.assertTrue(StageLockAPI.getPlayerStages(player.getUUID()).isEmpty(), "global stage leaked into player scope");
        StageLockAPI.global().revoke("test:global");
        helper.assertFalse(StageLockAPI.has(player, "test:global"), "global stage still effective after revoke");
        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void grantCanBeCancelled(GameTestHelper helper) {
        ServerPlayer player = TestPlayers.create(helper, net.minecraft.world.level.GameType.CREATIVE);
        java.util.function.Consumer<StageEvent.Grant> veto = event -> {
            if (event.getScope() == StageScope.PLAYER && event.getStage().equals("test:vetoed")) event.setCanceled(true);
        };
        NeoForge.EVENT_BUS.addListener(veto);
        try {
            helper.assertTrue(StageLockAPI.grant(player, "test:vetoed").isEmpty(), "cancelled grant reported a change");
            helper.assertFalse(StageLockAPI.has(player, "test:vetoed"), "cancelled grant was applied");
        } finally {
            NeoForge.EVENT_BUS.unregister(veto);
        }
        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void fakePlayersHaveNoStagesByDefault(GameTestHelper helper) {
        var fake = FakePlayerFactory.getMinecraft(helper.getLevel());
        helper.assertTrue(StageLockAPI.getStages(fake).size() == 0, "fake player should have no stages without config");
        helper.succeed();
    }
}
