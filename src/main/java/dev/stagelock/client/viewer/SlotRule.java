package dev.stagelock.client.viewer;

import dev.stagelock.core.config.ServerConfig.RecipeHiding;

import java.util.Collection;
import java.util.function.Predicate;

/**
 * Decides whether a recipe viewer entry gives away something hidden, from the contents of its slots.
 * Viewer-agnostic: each viewer plugin hands over its slots as plain lists of the options each slot can show.
 */
public final class SlotRule {

    private SlotRule() {
    }

    /**
     * {@code OUTPUT} never hides by slots: in that mode only a recipe's own result hides it, which the
     * recipe gate already decides for real recipes (the 0.1.2 behaviour).
     */
    public static <T> boolean hides(RecipeHiding mode, Iterable<? extends Collection<? extends T>> slots, Predicate<? super T> hidden) {
        if (mode == RecipeHiding.OUTPUT) return false;
        for (Collection<? extends T> slot : slots) {
            if (slot.isEmpty()) continue;
            if (mode == RecipeHiding.STRICT) {
                for (T option : slot) if (hidden.test(option)) return true;
            } else if (allHidden(slot, hidden)) {
                return true;
            }
        }
        return false;
    }

    private static <T> boolean allHidden(Collection<? extends T> options, Predicate<? super T> hidden) {
        for (T option : options) if (!hidden.test(option)) return false;
        return true;
    }
}
