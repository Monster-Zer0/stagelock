package dev.stagelock.gametest;

import dev.stagelock.StageLock;
import dev.stagelock.api.StageLockAPI;
import dev.stagelock.api.StageRequirement;
import dev.stagelock.api.restriction.ItemRestriction;
import dev.stagelock.api.restriction.RecipeRestriction;
import dev.stagelock.api.restriction.Restriction;
import dev.stagelock.core.recipe.BlockOwner;
import dev.stagelock.core.restriction.RestrictionManager;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.BeforeBatch;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.FurnaceBlockEntity;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@GameTestHolder(StageLock.MOD_ID)
@PrefixGameTestTemplate(false)
public final class RecipeGameTests {

    private static final String EMPTY = "empty";
    private static final String BATCH = "stagelock_recipes";

    @BeforeBatch(batch = BATCH)
    public static void installRules(ServerLevel level) {
        Map<ResourceLocation, Restriction> rules = new LinkedHashMap<>();
        rules.put(StageLock.id("test/planks"), recipe("test:woodworking", "minecraft:oak_planks"));
        rules.put(StageLock.id("test/smelt_iron"), recipe("test:smelting", "minecraft:iron_ingot_from_smelting_raw_iron"));
        TestPlayers.addRules(rules);
    }

    private static RecipeRestriction recipe(String stage, String id) {
        return new RecipeRestriction(StageRequirement.of(stage),
                List.of(RecipeRestriction.RecipeIdMatcher.parse(id).getOrThrow()), List.of(), List.of(), ItemRestriction.ViewerMode.HIDE);
    }

    @GameTest(template = EMPTY, batch = BATCH)
    public static void craftingGridLockedUntilUnlocked(GameTestHelper helper) {
        ServerPlayer player = TestPlayers.create(helper, GameType.SURVIVAL);
        var menu = player.inventoryMenu;
        menu.getSlot(1).set(new ItemStack(Items.OAK_LOG));
        helper.assertTrue(menu.getSlot(0).getItem().isEmpty(), "locked recipe produced " + menu.getSlot(0).getItem());

        StageLockAPI.grant(player, "test:woodworking");
        // The open grid is refreshed when the stage change is flushed at the end of the tick.
        helper.succeedWhen(() -> {
            helper.assertTrue(menu.getSlot(0).getItem().is(Items.OAK_PLANKS), "grid not refreshed after unlock");
            TestPlayers.remove(player);
        });
    }

    @GameTest(template = EMPTY, batch = BATCH, timeoutTicks = 600)
    public static void furnaceUsesOfflineOwnersStages(GameTestHelper helper) {
        BlockPos pos = new BlockPos(1, 1, 1);
        helper.setBlock(pos, Blocks.FURNACE);
        FurnaceBlockEntity furnace = (FurnaceBlockEntity) helper.getBlockEntity(pos);
        UUID owner = UUID.randomUUID();
        BlockOwner.set(furnace, owner);
        furnace.setItem(0, new ItemStack(Items.RAW_IRON));
        furnace.setItem(1, new ItemStack(Items.COAL));

        helper.runAfterDelay(260, () -> {
            helper.assertTrue(furnace.getItem(2).isEmpty(), "furnace smelted a locked recipe");
            helper.assertTrue(furnace.getItem(0).is(Items.RAW_IRON), "input was consumed");
            StageLockAPI.grant(owner, List.of("test:smelting"));
            helper.succeedWhen(() -> helper.assertTrue(furnace.getItem(2).is(Items.IRON_INGOT), "furnace did not smelt after owner unlocked"));
        });
    }

    @GameTest(template = EMPTY, batch = BATCH)
    public static void creativePlayersCraftAnything(GameTestHelper helper) {
        ServerPlayer player = TestPlayers.create(helper, GameType.CREATIVE);
        var menu = player.inventoryMenu;
        menu.getSlot(1).set(new ItemStack(Items.OAK_LOG));
        helper.assertTrue(menu.getSlot(0).getItem().is(Items.OAK_PLANKS), "creative player was gated");
        TestPlayers.remove(player);
        helper.succeed();
    }
}
