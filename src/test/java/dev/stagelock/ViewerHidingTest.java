package dev.stagelock;

import dev.stagelock.api.StageRequirement;
import dev.stagelock.api.StageSet;
import dev.stagelock.api.restriction.ItemMatcher;
import dev.stagelock.api.restriction.ItemRestriction;
import dev.stagelock.api.restriction.Restriction;
import dev.stagelock.core.restriction.RestrictionManager;
import dev.stagelock.core.restriction.ViewerHiding;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ViewerHidingTest {

    private static final RestrictionManager MANAGER = RestrictionManager.client();

    private static ItemRestriction rule(String stage, ItemRestriction.ViewerMode viewer) {
        var actions = new ItemRestriction.Actions(true, true, true, true, true, true, ItemRestriction.HoldMode.ALLOW, true, viewer);
        return new ItemRestriction(StageRequirement.of(stage), List.of(ItemMatcher.of("minecraft:diamond")), List.of(), actions,
                true, Optional.empty(), ItemRestriction.TooltipMode.REPLACE, Optional.empty());
    }

    @AfterEach
    void reset() {
        MANAGER.setRules(Map.of());
    }

    @Test
    void showOnlyRulesNeverRefresh() {
        MANAGER.setRules(Map.<ResourceLocation, Restriction>of(ResourceLocation.parse("t:a"), rule("a", ItemRestriction.ViewerMode.SHOW)));
        assertFalse(MANAGER.hidesAnythingInViewer());
        ViewerHiding locked = ViewerHiding.of(MANAGER, StageSet.EMPTY);
        ViewerHiding unlocked = ViewerHiding.of(MANAGER, StageSet.of(Set.of("a")));
        ViewerHiding creative = ViewerHiding.of(MANAGER, null);
        assertFalse(unlocked.changedFrom(locked), "stage grant must not refresh");
        assertFalse(creative.changedFrom(locked), "creative toggle must not refresh");
        assertFalse(locked.changedFrom(creative), "leaving creative must not refresh");
        assertFalse(locked.changedFrom(ViewerHiding.NONE), "joining must not refresh");
    }

    @Test
    void hideRuleRefreshesOnlyWhenItsStateFlips() {
        MANAGER.setRules(Map.<ResourceLocation, Restriction>of(
                ResourceLocation.parse("t:hide"), rule("a", ItemRestriction.ViewerMode.HIDE),
                ResourceLocation.parse("t:show"), rule("b", ItemRestriction.ViewerMode.SHOW)));
        assertTrue(MANAGER.hidesAnythingInViewer());
        assertEquals(Set.of(ResourceLocation.parse("t:hide")), MANAGER.viewerHidingRules().keySet());

        ViewerHiding locked = ViewerHiding.of(MANAGER, StageSet.EMPTY);
        assertTrue(ViewerHiding.of(MANAGER, StageSet.of(Set.of("a"))).changedFrom(locked), "unlocking a hidden item must refresh");
        assertFalse(ViewerHiding.of(MANAGER, StageSet.of(Set.of("b"))).changedFrom(locked), "unrelated stage must not refresh");
        assertTrue(ViewerHiding.of(MANAGER, null).changedFrom(locked), "creative shows hidden items, so it must refresh");
    }

    @Test
    void removingHideRulesRefreshesOnce() {
        MANAGER.setRules(Map.<ResourceLocation, Restriction>of(ResourceLocation.parse("t:hide"), rule("a", ItemRestriction.ViewerMode.HIDE)));
        ViewerHiding before = ViewerHiding.of(MANAGER, StageSet.EMPTY);
        MANAGER.setRules(Map.of());
        ViewerHiding after = ViewerHiding.of(MANAGER, StageSet.EMPTY);
        assertTrue(after.changedFrom(before), "items hidden by a removed rule must come back");
        assertFalse(ViewerHiding.of(MANAGER, StageSet.EMPTY).changedFrom(after));
    }
}
