package dev.stagelock.core.restriction;

import dev.stagelock.api.StageSet;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

import java.util.Set;
import java.util.TreeSet;

/**
 * Fingerprint of what recipe viewers should hide for one player: the viewer-hiding rules that are
 * currently in effect, plus the rule generation they were evaluated against.
 * <p>
 * What a viewer hides is a function of these alone, so two equal fingerprints mean a viewer refresh
 * would change nothing. When neither fingerprint hides anything, rule reloads don't matter either.
 */
public record ViewerHiding(int generation, Set<ResourceLocation> active) {

    public static final ViewerHiding NONE = new ViewerHiding(-1, Set.of());

    /** {@code stages == null} means the player is exempt (creative/spectator/op bypass): nothing is hidden. */
    public static ViewerHiding of(RestrictionManager manager, @Nullable StageSet stages) {
        if (stages == null || !manager.hidesAnythingInViewer()) return new ViewerHiding(manager.generation(), Set.of());
        Set<ResourceLocation> active = new TreeSet<>();
        manager.viewerHidingRules().forEach((id, requirement) -> {
            if (!requirement.test(stages)) active.add(id);
        });
        return new ViewerHiding(manager.generation(), Set.copyOf(active));
    }

    /** Whether moving from {@code previous} to this state can change what viewers show. */
    public boolean changedFrom(ViewerHiding previous) {
        if (active.isEmpty() && previous.active.isEmpty()) return false;
        return !equals(previous);
    }
}
