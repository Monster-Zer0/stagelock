package dev.stagelock;

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import dev.stagelock.api.StageRequirement;
import dev.stagelock.api.restriction.ItemAction;
import dev.stagelock.api.restriction.ItemMatcher;
import dev.stagelock.api.restriction.ItemRestriction;
import dev.stagelock.api.restriction.Restriction;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RestrictionCodecTest {

    private static Restriction parse(String json) {
        return Restriction.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString(json)).getOrThrow();
    }

    @Test
    void parsesDocumentedItemRule() {
        Restriction parsed = parse("""
                {
                  "type": "stagelock:item",
                  "requires": "mypack:iron_age",
                  "items": ["minecraft:diamond", "#c:gems/diamond", "@mekanism"],
                  "except": ["mekanism:configurator"],
                  "actions": { "pickup": false, "hold": "allow", "viewer": "show" },
                  "hidden_name": "Shiny Rock",
                  "tooltip": "append"
                }
                """);
        ItemRestriction rule = (ItemRestriction) parsed;
        assertEquals(StageRequirement.of("mypack:iron_age"), rule.requires());
        assertEquals(new ItemMatcher.Id(ResourceLocation.parse("minecraft:diamond")), rule.items().get(0));
        assertTrue(rule.items().get(1) instanceof ItemMatcher.Tag);
        assertEquals(new ItemMatcher.Mod("mekanism"), rule.items().get(2));
        assertFalse(rule.blocks(ItemAction.PICKUP));
        assertFalse(rule.blocks(ItemAction.HOLD));
        assertFalse(rule.blocks(ItemAction.VIEWER));
        assertTrue(rule.blocks(ItemAction.USE));
        assertEquals("Shiny Rock", rule.displayName().getString());
        assertEquals(ItemRestriction.TooltipMode.APPEND, rule.tooltip());
    }

    @Test
    void defaultsBlockEverything() {
        ItemRestriction rule = (ItemRestriction) parse("""
                {"type": "stagelock:item", "requires": "a", "items": "minecraft:stick"}
                """);
        for (ItemAction action : ItemAction.values()) assertTrue(rule.blocks(action), action.name());
        assertTrue(rule.hideName());
    }

    @Test
    void holdImpliesNoPickup() {
        ItemRestriction rule = (ItemRestriction) parse("""
                {"type": "stagelock:item", "requires": "a", "items": "minecraft:stick", "actions": {"pickup": false}}
                """);
        assertTrue(rule.blocks(ItemAction.PICKUP), "an item that is dropped on hold must not be picked back up");
    }

    @Test
    void stageDefinitionSurvivesUnknownIcon() {
        var def = dev.stagelock.api.StageDefinition.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString(
                "{\"icon\": \"missingmod:thing\", \"requires\": [\"a\"]}")).getOrThrow();
        assertEquals(java.util.List.of("a"), def.requires());
        assertTrue(def.iconItem().isEmpty());
    }

    @Test
    void rejectsUnknownTypeAndBadMatchers() {
        assertTrue(Restriction.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString(
                "{\"type\": \"stagelock:nope\", \"requires\": \"a\"}")).isError());
        assertTrue(Restriction.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString(
                "{\"type\": \"stagelock:item\", \"requires\": \"a\", \"items\": \"@\"}")).isError());
    }

    @Test
    void viewerCategoryRuleAcceptsOneIdOrAList() {
        var one = (dev.stagelock.api.restriction.ViewerCategoryRestriction) parse("""
                {"type": "stagelock:viewer_category", "requires": "iron_age", "categories": "minecraft:anvil"}
                """);
        assertEquals(java.util.List.of(ResourceLocation.parse("minecraft:anvil")), one.categories());
        var many = (dev.stagelock.api.restriction.ViewerCategoryRestriction) parse("""
                {"type": "stagelock:viewer_category", "requires": "iron_age", "categories": ["minecraft:anvil", "emi:anvil_repairing"]}
                """);
        assertEquals(2, many.categories().size());
        var manager = dev.stagelock.core.restriction.RestrictionManager.client();
        manager.setRules(java.util.Map.of(ResourceLocation.parse("t:cat"), many));
        try {
            assertTrue(manager.hidesAnythingInViewer(), "a category rule must make viewers refresh when it flips");
        } finally {
            manager.setRules(java.util.Map.of());
        }
    }
}
