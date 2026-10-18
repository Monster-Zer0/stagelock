package dev.stagelock.gametest;

import dev.stagelock.StageLock;
import dev.stagelock.api.StageLockAPI;
import dev.stagelock.api.StageRequirement;
import dev.stagelock.api.restriction.ItemMatcher;
import dev.stagelock.api.restriction.ItemRestriction;
import dev.stagelock.api.restriction.Restriction;
import dev.stagelock.core.restriction.RestrictionManager;
import net.minecraft.gametest.framework.BeforeBatch;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@GameTestHolder(StageLock.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ItemGameTests {

    private static final String EMPTY = "empty";
    private static final String BATCH = "stagelock_items";

    private static final ItemRestriction.Actions USE_ONLY = new ItemRestriction.Actions(
            false, true, true, true, true, true, ItemRestriction.HoldMode.ALLOW, true, ItemRestriction.ViewerMode.HIDE);

    @BeforeBatch(batch = BATCH)
    public static void installRules(ServerLevel level) {
        Map<ResourceLocation, Restriction> rules = new LinkedHashMap<>();
        rules.put(StageLock.id("test/diamond"), rule("minecraft:diamond", "test:gems", ItemRestriction.Actions.ALL));
        rules.put(StageLock.id("test/emerald"), rule("minecraft:emerald", "test:gems", ItemRestriction.Actions.ALL));
        rules.put(StageLock.id("test/snowball"), rule("minecraft:snowball", "test:throwing", USE_ONLY));
        // Two rules on one item: a loose one (like the example pack's diamond gear) listed first, a strict one second.
        rules.put(StageLock.id("test/pickaxe_loose"), rule("minecraft:golden_pickaxe", "test:loose", holding(ItemRestriction.HoldMode.ALLOW)));
        rules.put(StageLock.id("test/pickaxe_strict"), rule("minecraft:golden_pickaxe", "test:strict", holding(ItemRestriction.HoldMode.DROP)));
        // Two rules that both block holding, the milder one first.
        rules.put(StageLock.id("test/axe_drop"), rule("minecraft:golden_axe", "test:axe_drop", holding(ItemRestriction.HoldMode.DROP)));
        rules.put(StageLock.id("test/axe_destroy"), rule("minecraft:golden_axe", "test:axe_destroy", holding(ItemRestriction.HoldMode.DESTROY)));
        TestPlayers.addRules(rules);
    }

    private static ItemRestriction.Actions holding(ItemRestriction.HoldMode hold) {
        return new ItemRestriction.Actions(false, true, true, true, true, true, hold, true, ItemRestriction.ViewerMode.SHOW);
    }

    private static ItemRestriction rule(String item, String stage, ItemRestriction.Actions actions) {
        return new ItemRestriction(StageRequirement.of(stage), List.of(ItemMatcher.of(item)), List.of(), actions,
                true, Optional.empty(), ItemRestriction.TooltipMode.REPLACE, Optional.empty());
    }

    @GameTest(template = EMPTY, batch = BATCH)
    public static void pickupBlockedUntilUnlocked(GameTestHelper helper) {
        ServerPlayer player = TestPlayers.create(helper, GameType.SURVIVAL);
        ItemEntity item = new ItemEntity(helper.getLevel(), player.getX(), player.getY(), player.getZ(), new ItemStack(Items.DIAMOND));
        item.setNoPickUpDelay();
        helper.getLevel().addFreshEntity(item);

        item.playerTouch(player);
        helper.assertFalse(player.getInventory().contains(new ItemStack(Items.DIAMOND)), "picked up a locked item");

        StageLockAPI.grant(player, "test:gems");
        item.playerTouch(player);
        helper.assertTrue(player.getInventory().contains(new ItemStack(Items.DIAMOND)), "could not pick up after unlock");
        TestPlayers.remove(player);
        helper.succeed();
    }

    @GameTest(template = EMPTY, batch = BATCH)
    public static void lockedItemsAreDroppedFromInventory(GameTestHelper helper) {
        ServerPlayer player = TestPlayers.create(helper, GameType.SURVIVAL);
        player.getInventory().add(new ItemStack(Items.EMERALD, 5));
        // Mock players have no real connection, so the server never ticks them; do what the network handler would.
        helper.onEachTick(player::doTick);
        helper.succeedWhen(() -> {
            helper.assertFalse(player.getInventory().contains(new ItemStack(Items.EMERALD)), "locked item still in inventory");
            helper.assertEntityPresent(EntityType.ITEM);
            TestPlayers.remove(player);
        });
    }

    @GameTest(template = EMPTY, batch = BATCH)
    public static void useBlockedUntilUnlocked(GameTestHelper helper) {
        ServerPlayer player = TestPlayers.create(helper, GameType.SURVIVAL);
        ItemStack snowballs = new ItemStack(Items.SNOWBALL, 4);
        player.setItemInHand(InteractionHand.MAIN_HAND, snowballs);

        InteractionResult blocked = player.gameMode.useItem(player, helper.getLevel(), snowballs, InteractionHand.MAIN_HAND);
        helper.assertTrue(blocked == InteractionResult.FAIL, "use was not blocked: " + blocked);
        helper.assertTrue(snowballs.getCount() == 4, "snowball consumed while locked");

        StageLockAPI.grant(player, "test:throwing");
        player.gameMode.useItem(player, helper.getLevel(), snowballs, InteractionHand.MAIN_HAND);
        helper.assertTrue(snowballs.getCount() == 3, "snowball not thrown after unlock");
        TestPlayers.remove(player);
        helper.succeed();
    }

    /** Items written straight into the inventory list (e.g. /give, other mods) don't bump the change counter. */
    @GameTest(template = EMPTY, batch = BATCH)
    public static void directInsertIsRemovedWithinRescanInterval(GameTestHelper helper) {
        ServerPlayer player = TestPlayers.create(helper, GameType.SURVIVAL);
        helper.onEachTick(player::doTick);
        int interval = dev.stagelock.core.config.ServerConfig.INVENTORY_RESCAN_TICKS.get();
        helper.assertTrue(interval > 0, "periodic rescan disabled in config");
        helper.runAfterDelay(5, () -> {
            int changes = player.getInventory().getTimesChanged();
            player.getInventory().items.set(3, new ItemStack(Items.EMERALD));
            helper.assertTrue(player.getInventory().getTimesChanged() == changes, "test setup bumped the change counter");
            helper.runAfterDelay(interval + 2, () -> {
                helper.assertFalse(player.getInventory().contains(new ItemStack(Items.EMERALD)), "directly inserted locked item was kept");
                TestPlayers.remove(player);
                helper.succeed();
            });
        });
    }

    @GameTest(template = EMPTY, batch = BATCH)
    public static void looserRuleDoesNotHideStricterOne(GameTestHelper helper) {
        ServerPlayer player = TestPlayers.create(helper, GameType.SURVIVAL);
        helper.onEachTick(player::doTick);
        ItemStack pickaxe = new ItemStack(Items.GOLDEN_PICKAXE);
        var hit = dev.stagelock.core.enforce.Restrictions.item(player, pickaxe, dev.stagelock.api.restriction.ItemAction.HOLD);
        helper.assertTrue(hit != null && hit.id().equals(StageLock.id("test/pickaxe_strict")), "hold decided by " + (hit == null ? null : hit.id()));
        player.getInventory().add(pickaxe.copy());
        helper.succeedWhen(() -> {
            helper.assertFalse(player.getInventory().contains(new ItemStack(Items.GOLDEN_PICKAXE)), "strict hold: drop rule did not apply");
            TestPlayers.remove(player);
        });
    }

    @GameTest(template = EMPTY, batch = BATCH)
    public static void strictestHoldModeWins(GameTestHelper helper) {
        ServerPlayer player = TestPlayers.create(helper, GameType.SURVIVAL);
        helper.onEachTick(player::doTick);
        ItemStack axe = new ItemStack(Items.GOLDEN_AXE);
        var hit = dev.stagelock.core.enforce.Restrictions.item(player, axe, dev.stagelock.api.restriction.ItemAction.HOLD);
        helper.assertTrue(hit != null && hit.id().equals(StageLock.id("test/axe_destroy")), "hold decided by " + (hit == null ? null : hit.id()));
        player.getInventory().add(axe.copy());
        helper.runAfterDelay(5, () -> {
            helper.assertFalse(player.getInventory().contains(new ItemStack(Items.GOLDEN_AXE)), "axe still held");
            helper.assertTrue(helper.getLevel().getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,
                    player.getBoundingBox().inflate(4), e -> e.getItem().is(Items.GOLDEN_AXE)).isEmpty(), "axe was dropped instead of destroyed");
            // Unlocking the destroy rule leaves the drop rule in charge.
            StageLockAPI.grant(player, "test:axe_destroy");
            var after = dev.stagelock.core.enforce.Restrictions.item(player, new ItemStack(Items.GOLDEN_AXE), dev.stagelock.api.restriction.ItemAction.HOLD);
            helper.assertTrue(after != null && after.id().equals(StageLock.id("test/axe_drop")), "after unlock, hold decided by " + (after == null ? null : after.id()));
            TestPlayers.remove(player);
            helper.succeed();
        });
    }

    /** Exercises the real datapack path (RegistryOps, component predicates) and the client sync codec. */
    @GameTest(template = EMPTY)
    public static void loaderAndSyncRoundTrip(GameTestHelper helper) {
        var registries = helper.getLevel().registryAccess();
        var json = com.google.gson.JsonParser.parseString("""
                {"rules": [
                  {"type": "stagelock:item", "requires": "a", "items": ["#minecraft:logs", "@minecraft"], "except": "minecraft:dirt"},
                  {"type": "stagelock:item", "requires": {"any_of": ["a", "b"]},
                   "items": {"items": "minecraft:diamond_sword", "components": {"minecraft:damage": 5}}},
                  {"type": "stagelock:item", "requires": "a", "items": "has space"}
                ]}
                """);
        // Apply into a scratch manager by parsing exactly like the loader does.
        var ops = net.minecraft.resources.RegistryOps.create(com.mojang.serialization.JsonOps.INSTANCE, registries);
        Map<ResourceLocation, Restriction> parsed = new LinkedHashMap<>();
        var array = json.getAsJsonObject().getAsJsonArray("rules");
        for (int i = 0; i < array.size(); i++) {
            int index = i;
            Restriction.CODEC.parse(ops, array.get(i)).ifSuccess(r -> parsed.put(StageLock.id("load/" + index), r));
        }
        helper.assertTrue(parsed.size() == 2, "expected 2 valid rules and 1 rejected, got " + parsed.size());

        ItemRestriction sword = (ItemRestriction) parsed.get(StageLock.id("load/1"));
        ItemStack damaged = new ItemStack(Items.DIAMOND_SWORD);
        damaged.setDamageValue(5);
        helper.assertTrue(sword.matches(damaged), "component predicate did not match damaged sword");
        helper.assertFalse(sword.matches(new ItemStack(Items.DIAMOND_SWORD)), "component predicate matched undamaged sword");

        ItemRestriction logs = (ItemRestriction) parsed.get(StageLock.id("load/0"));
        helper.assertTrue(logs.matches(new ItemStack(Items.OAK_LOG)), "tag matcher failed");
        helper.assertFalse(logs.matches(new ItemStack(Items.DIRT)), "except did not exclude dirt");

        var buf = new net.minecraft.network.RegistryFriendlyByteBuf(io.netty.buffer.Unpooled.buffer(), registries);
        var payload = new dev.stagelock.network.SyncRestrictionsPayload(parsed);
        dev.stagelock.network.SyncRestrictionsPayload.STREAM_CODEC.encode(buf, payload);
        var decoded = dev.stagelock.network.SyncRestrictionsPayload.STREAM_CODEC.decode(buf);
        helper.assertTrue(decoded.rules().keySet().equals(parsed.keySet()), "sync lost rules");
        ItemRestriction syncedSword = (ItemRestriction) decoded.rules().get(StageLock.id("load/1"));
        helper.assertTrue(syncedSword.matches(damaged) && syncedSword.requires().equals(sword.requires()), "sync changed the rule");
        helper.succeed();
    }

    @GameTest(template = EMPTY, batch = BATCH)
    public static void creativePlayersAreExempt(GameTestHelper helper) {
        ServerPlayer player = TestPlayers.create(helper, GameType.CREATIVE);
        player.getInventory().add(new ItemStack(Items.DIAMOND));
        helper.onEachTick(player::doTick);
        helper.runAfterDelay(5, () -> {
            helper.assertTrue(player.getInventory().contains(new ItemStack(Items.DIAMOND)), "creative player lost a locked item");
            TestPlayers.remove(player);
            helper.succeed();
        });
    }
}
