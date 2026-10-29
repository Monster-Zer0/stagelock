package dev.stagelock;

import dev.stagelock.client.viewer.SlotRule;
import dev.stagelock.core.config.ServerConfig.RecipeHiding;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SlotRuleTest {

    private static final Predicate<String> HIDDEN = Set.of("iron_sword", "iron_ingot")::contains;

    private static boolean hides(RecipeHiding mode, List<List<String>> slots) {
        return SlotRule.hides(mode, slots, HIDDEN);
    }

    /** Anvil repair: the sword in, the sword out. */
    private static final List<List<String>> REPAIR = List.of(
            List.of("iron_sword"), List.of("iron_ingot"), List.of("iron_sword"));

    /** Smelter: the sword in, nuggets out. */
    private static final List<List<String>> SMELT = List.of(
            List.of("iron_sword", "iron_axe"), List.of("iron_nugget"));

    /** A recipe that takes any ingot, one of which is hidden. */
    private static final List<List<String>> ANY_INGOT = List.of(
            List.of("copper_ingot", "iron_ingot", "gold_ingot"), List.of("plate"));

    @Test
    void slotModeHidesEntriesWithAnOnlyHiddenSlot() {
        assertTrue(hides(RecipeHiding.SLOT, REPAIR));
        assertTrue(hides(RecipeHiding.SLOT, List.of(List.of("iron_sword"), List.of("iron_nugget"))),
                "an input-only use of the hidden item must hide the entry");
        assertFalse(hides(RecipeHiding.SLOT, SMELT), "a slot that still has a visible option keeps the entry");
        assertFalse(hides(RecipeHiding.SLOT, ANY_INGOT));
    }

    @Test
    void strictModeHidesAnyMention() {
        assertTrue(hides(RecipeHiding.STRICT, REPAIR));
        assertTrue(hides(RecipeHiding.STRICT, SMELT));
        assertTrue(hides(RecipeHiding.STRICT, ANY_INGOT));
        assertFalse(hides(RecipeHiding.STRICT, List.of(List.of("stick"), List.of("torch"))));
    }

    @Test
    void outputModeLeavesEntriesToTheRecipeGate() {
        assertFalse(hides(RecipeHiding.OUTPUT, REPAIR));
        assertFalse(hides(RecipeHiding.OUTPUT, SMELT));
    }

    @Test
    void emptySlotsNeverHide() {
        assertFalse(hides(RecipeHiding.SLOT, List.of(List.of(), List.of())));
        assertFalse(hides(RecipeHiding.STRICT, List.of(List.of())));
    }
}
