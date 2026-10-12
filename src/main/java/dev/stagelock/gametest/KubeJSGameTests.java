package dev.stagelock.gametest;

import dev.stagelock.StageLock;
import dev.stagelock.api.StageLockAPI;
import dev.stagelock.api.restriction.ItemAction;
import dev.stagelock.api.restriction.ItemRestriction;
import dev.stagelock.api.restriction.RecipeRestriction;
import dev.stagelock.core.definition.StageDefinitions;
import dev.stagelock.core.restriction.RestrictionManager;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Only meaningful in a dev run with KubeJS ({@code -Pdev_kubejs=true}) and the script in
 * {@code run/kubejs/server_scripts/stagelock_test.js}; passes trivially otherwise.
 */
@GameTestHolder(StageLock.MOD_ID)
@PrefixGameTestTemplate(false)
public final class KubeJSGameTests {

    @GameTest(template = "empty", batch = "stagelock_kubejs")
    public static void scriptsDefineRulesAndReactToStages(GameTestHelper helper) {
        if (!ModList.get().isLoaded("kubejs")) {
            helper.succeed();
            return;
        }
        var rules = RestrictionManager.server().rules();
        var item = (ItemRestriction) rules.get(ResourceLocation.parse("kubejs:rule_0"));
        helper.assertTrue(item != null, "script item rule missing; rules = " + rules.keySet());
        helper.assertTrue(item.blocks(ItemAction.USE) && !item.blocks(ItemAction.ATTACK), "only() not applied");
        helper.assertTrue(item.displayName().getString().equals("Red Dust"), "hideName not applied");
        helper.assertTrue(rules.get(ResourceLocation.parse("kubejs:rule_1")) instanceof RecipeRestriction, "recipe rule missing");
        helper.assertTrue(rules.size() >= 4, "expected 4 script rules, got " + rules.keySet());
        helper.assertTrue(StageDefinitions.server().isDefined("kjs:child"), "script stage definition missing");

        ServerPlayer player = TestPlayers.create(helper, GameType.SURVIVAL);
        StageLockAPI.grant(player, "kjs:ping");
        helper.assertTrue(StageLockAPI.has(player, "kjs:pong"), "StageLockEvents.granted handler did not run");
        StageLockAPI.grant(player, "kjs:builtin");
        helper.assertTrue(StageLockAPI.has(player, "kjs:builtin_seen"), "PlayerEvents.stageAdded / player.stages bridge did not run");
        StageLockAPI.grant(player, "kjs:child");
        helper.assertTrue(StageLockAPI.has(player, "kjs:parent"), "script-defined prerequisite not granted");
        TestPlayers.remove(player);
        helper.succeed();
    }
}
