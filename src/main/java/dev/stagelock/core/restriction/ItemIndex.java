package dev.stagelock.core.restriction;

import dev.stagelock.api.StageSet;
import dev.stagelock.api.restriction.ItemAction;
import dev.stagelock.api.restriction.ItemRestriction;
import dev.stagelock.api.restriction.ItemMatcher;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Item rules indexed by item, so a lookup is one hash lookup plus a short list instead of testing
 * every rule. Rules whose selectors can match anything (component-only predicates) go in a wildcard
 * list that is checked for every stack.
 */
public final class ItemIndex {

    public static final ItemIndex EMPTY = new ItemIndex(Map.of(), List.of());

    private final Map<Item, List<RestrictionManager.Entry<ItemRestriction>>> byItem;
    private final List<RestrictionManager.Entry<ItemRestriction>> wildcard;

    private ItemIndex(Map<Item, List<RestrictionManager.Entry<ItemRestriction>>> byItem,
                      List<RestrictionManager.Entry<ItemRestriction>> wildcard) {
        this.byItem = byItem;
        this.wildcard = wildcard;
    }

    static ItemIndex compile(Map<ResourceLocation, ItemRestriction> rules) {
        if (rules.isEmpty()) return EMPTY;
        Map<Item, List<RestrictionManager.Entry<ItemRestriction>>> byItem = new IdentityHashMap<>();
        List<RestrictionManager.Entry<ItemRestriction>> wildcard = new ArrayList<>();
        rules.forEach((id, rule) -> {
            var entry = new RestrictionManager.Entry<>(id, rule);
            Set<Item> candidates = java.util.Collections.newSetFromMap(new IdentityHashMap<>());
            boolean bounded = true;
            for (ItemMatcher matcher : rule.items()) {
                bounded &= matcher.collectCandidates(candidates::add);
            }
            if (!bounded) {
                wildcard.add(entry);
            } else {
                for (Item item : candidates) byItem.computeIfAbsent(item, k -> new ArrayList<>(1)).add(entry);
            }
        });
        return new ItemIndex(byItem, List.copyOf(wildcard));
    }

    /**
     * Returns the rule that decides {@code action} for this stack: among the rules that block it and
     * whose requirement the stages do not satisfy, the most restrictive one (see {@link #strictness}).
     * Equally strict rules resolve to the first in load order. A {@code null} action considers every
     * locked rule (used for tooltips, hidden names and messages).
     */
    @Nullable
    public RestrictionManager.Entry<ItemRestriction> find(ItemStack stack, StageSet stages, @Nullable ItemAction action) {
        if (stack.isEmpty()) return null;
        var hit = scan(byItem.get(stack.getItem()), stack, stages, action, null);
        return scan(wildcard, stack, stages, action, hit);
    }

    /**
     * How restrictive a rule is for an action, so the strictest of several locking rules decides.
     * For {@code hold}: destroy > drop. For {@code viewer}: hide > lock > show. Other actions are plain
     * yes/no, so any blocking rule decides. With no specific action, hold mode counts most, then
     * viewer mode, then how many actions the rule blocks.
     */
    static int strictness(ItemRestriction rule, @Nullable ItemAction action) {
        var actions = rule.actions();
        int hold = actions.hold().ordinal();
        int viewer = switch (actions.viewer()) {
            case HIDE -> 2;
            case LOCK -> 1;
            case SHOW -> 0;
        };
        if (action == ItemAction.HOLD) return hold;
        if (action == ItemAction.VIEWER) return viewer;
        if (action != null) return 0;
        int blocked = 0;
        for (ItemAction each : ItemAction.values()) {
            if (rule.blocks(each)) blocked++;
        }
        return hold * 100 + viewer * 20 + blocked;
    }

    /** Every rule matching the stack, regardless of stages or actions (for debugging commands). */
    public List<RestrictionManager.Entry<ItemRestriction>> matching(ItemStack stack) {
        List<RestrictionManager.Entry<ItemRestriction>> out = new ArrayList<>();
        for (var entry : byItem.getOrDefault(stack.getItem(), List.of())) {
            if (entry.rule().matches(stack)) out.add(entry);
        }
        for (var entry : wildcard) {
            if (entry.rule().matches(stack)) out.add(entry);
        }
        return out;
    }

    /** Every indexed item, for recipe viewers. Wildcard rules are not included. */
    public Set<Item> indexedItems() {
        return byItem.keySet();
    }

    public boolean hasWildcards() {
        return !wildcard.isEmpty();
    }

    @Nullable
    private static RestrictionManager.Entry<ItemRestriction> scan(@Nullable List<RestrictionManager.Entry<ItemRestriction>> entries,
                                                                  ItemStack stack, StageSet stages, @Nullable ItemAction action,
                                                                  @Nullable RestrictionManager.Entry<ItemRestriction> best) {
        if (entries == null) return best;
        int bestStrictness = best == null ? -1 : strictness(best.rule(), action);
        for (var entry : entries) {
            ItemRestriction rule = entry.rule();
            if (action != null && !rule.blocks(action)) continue;
            if (rule.requires().test(stages)) continue;
            int strictness = strictness(rule, action);
            if (strictness <= bestStrictness || !rule.matches(stack)) continue;
            best = entry;
            bestStrictness = strictness;
        }
        return best;
    }
}
