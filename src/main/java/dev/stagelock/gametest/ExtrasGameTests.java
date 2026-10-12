package dev.stagelock.gametest;

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import dev.stagelock.StageLock;
import dev.stagelock.api.StageLockAPI;
import dev.stagelock.api.StageDefinition;
import dev.stagelock.api.StageRequirement;
import dev.stagelock.api.restriction.BlockRestriction;
import dev.stagelock.api.restriction.EnchantmentRestriction;
import dev.stagelock.api.restriction.FluidRestriction;
import dev.stagelock.api.restriction.ItemAction;
import dev.stagelock.api.restriction.ItemRestriction;
import dev.stagelock.api.restriction.RegistryMatcher;
import dev.stagelock.api.restriction.Restriction;
import dev.stagelock.core.enforce.Restrictions;
import dev.stagelock.core.stage.StageStore;
import dev.stagelock.core.tools.GameStagesImport;
import dev.stagelock.core.tools.RuleDump;
import net.minecraft.advancements.Advancement;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.BeforeBatch;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.StringTag;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;

@GameTestHolder(StageLock.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ExtrasGameTests {

    private static final String EMPTY = "empty";
    private static final String BATCH = "stagelock_extras";

    @BeforeBatch(batch = BATCH)
    public static void installRules(ServerLevel level) {
        Map<ResourceLocation, Restriction> rules = new LinkedHashMap<>();
        rules.put(StageLock.id("test/lava"), new FluidRestriction(StageRequirement.of("test:lava"),
                List.of(RegistryMatcher.parse(Registries.FLUID, "#minecraft:lava").getOrThrow()), FluidRestriction.Actions.DEFAULT, Optional.empty()));
        rules.put(StageLock.id("test/mending"), new EnchantmentRestriction(StageRequirement.of("test:mending"),
                List.of(RegistryMatcher.parse(Registries.ENCHANTMENT, "minecraft:mending").getOrThrow()), true,
                ItemRestriction.Actions.ALL, Optional.empty()));
        rules.put(StageLock.id("test/disguised_ore"), new BlockRestriction(StageRequirement.of("test:ores"),
                List.of(RegistryMatcher.parse(Registries.BLOCK, "minecraft:emerald_ore").getOrThrow()), List.of(),
                new BlockRestriction.Actions(false, false, false, false), Optional.of(Blocks.STONE), Optional.empty()));
        TestPlayers.addRules(rules);
    }

    @GameTest(template = EMPTY, batch = BATCH)
    public static void lockedFluidCannotBePoured(GameTestHelper helper) {
        ServerPlayer player = TestPlayers.create(helper, GameType.SURVIVAL);
        ItemStack bucket = new ItemStack(Items.LAVA_BUCKET);
        player.setItemInHand(InteractionHand.MAIN_HAND, bucket);
        InteractionResult result = player.gameMode.useItem(player, helper.getLevel(), bucket, InteractionHand.MAIN_HAND);
        helper.assertTrue(result == InteractionResult.FAIL, "pouring locked lava was not blocked: " + result);
        TestPlayers.remove(player);
        helper.succeed();
    }

    @GameTest(template = EMPTY, batch = BATCH)
    public static void enchantedItemsAreRestricted(GameTestHelper helper) {
        ServerPlayer player = TestPlayers.create(helper, GameType.SURVIVAL);
        var mending = helper.getLevel().registryAccess().registryOrThrow(Registries.ENCHANTMENT).getHolderOrThrow(Enchantments.MENDING);
        ItemStack sword = new ItemStack(Items.IRON_SWORD);
        sword.enchant(mending, 1);
        ItemStack book = new ItemStack(Items.ENCHANTED_BOOK);
        var stored = new ItemEnchantments.Mutable(ItemEnchantments.EMPTY);
        stored.set(mending, 1);
        book.set(DataComponents.STORED_ENCHANTMENTS, stored.toImmutable());

        helper.assertTrue(Restrictions.isBlocked(player, sword, ItemAction.USE), "mending sword not restricted");
        helper.assertTrue(Restrictions.isBlocked(player, book, ItemAction.HOLD), "mending book not restricted");
        helper.assertFalse(Restrictions.isBlocked(player, new ItemStack(Items.IRON_SWORD), ItemAction.USE), "plain sword restricted");
        StageLockAPI.grant(player, "test:mending");
        helper.assertFalse(Restrictions.isBlocked(player, sword, ItemAction.USE), "still restricted after unlock");
        TestPlayers.remove(player);
        helper.succeed();
    }

    @GameTest(template = EMPTY, batch = BATCH)
    public static void disguisedBlockDropsItsDisguise(GameTestHelper helper) {
        ServerPlayer player = TestPlayers.create(helper, GameType.SURVIVAL);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.DIAMOND_PICKAXE));
        BlockPos pos = new BlockPos(1, 1, 1);
        helper.setBlock(pos, Blocks.EMERALD_ORE);
        helper.assertTrue(player.gameMode.destroyBlock(helper.absolutePos(pos)), "disguised block could not be broken");
        helper.assertItemEntityPresent(Items.COBBLESTONE, pos, 2);
        helper.assertItemEntityNotPresent(Items.EMERALD, pos, 2);
        TestPlayers.remove(player);
        helper.succeed();
    }

    /** Every file in the bundled example pack must parse against the real registries. */
    @GameTest(template = EMPTY)
    public static void examplePackParses(GameTestHelper helper) throws Exception {
        var ops = RegistryOps.create(JsonOps.INSTANCE, helper.getLevel().registryAccess());
        Path root = ModList.get().getModFileById(StageLock.MOD_ID).getFile().findResource("datapacks/examples/data/stagelock_examples");
        int parsed = 0;
        try (Stream<Path> files = Files.walk(root)) {
            for (Path file : files.filter(f -> f.toString().endsWith(".json")).toList()) {
                var json = JsonParser.parseString(Files.readString(file));
                String path = root.relativize(file).toString().replace('\\', '/');
                var result = path.startsWith("stagelock/restrictions/") ? Restriction.CODEC.parse(ops, json)
                        : path.startsWith("stagelock/stages/") ? StageDefinition.CODEC.parse(ops, json)
                        : Advancement.CODEC.parse(ops, json);
                helper.assertTrue(result.isSuccess(), path + ": " + result.error().map(e -> e.message()).orElse(""));
                parsed++;
            }
        }
        helper.assertTrue(parsed >= 12, "expected the example pack files, found " + parsed);
        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void importsGameStagesData(GameTestHelper helper) throws Exception {
        var server = helper.getLevel().getServer();
        Path dir = GameStagesImport.directory(server);
        Files.createDirectories(dir);
        UUID player = UUID.randomUUID();
        CompoundTag tag = new CompoundTag();
        ListTag stages = new ListTag();
        stages.add(StringTag.valueOf("legacy_one"));
        stages.add(StringTag.valueOf("Legacy:Two"));
        stages.add(StringTag.valueOf("bad stage"));
        tag.put("Stages", stages);
        Path file = dir.resolve(player + ".dat");
        NbtIo.writeCompressed(tag, file);
        try {
            var result = GameStagesImport.run(server);
            helper.assertTrue(result.skipped() >= 1, "invalid stage not skipped");
            helper.assertTrue(StageStore.get(server).player(player).equals(java.util.Set.of("legacy_one", "legacy:two")),
                    "imported " + StageStore.get(server).player(player));
        } finally {
            Files.deleteIfExists(file);
            StageLockAPI.clear(player);
        }
        helper.succeed();
    }

    @GameTest(template = EMPTY, batch = BATCH)
    public static void dumpListsRules(GameTestHelper helper) throws Exception {
        Path file = Files.createTempFile("stagelock-dump", ".md");
        try {
            RuleDump.write(helper.getLevel().getServer(), file);
            String text = Files.readString(file);
            helper.assertTrue(text.contains("stagelock:test/lava") && text.contains("\"type\": \"stagelock:fluid\""), "dump missing rules");
        } finally {
            Files.deleteIfExists(file);
        }
        helper.succeed();
    }
}
