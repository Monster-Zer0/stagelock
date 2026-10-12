package dev.stagelock.compat.kubejs;

import com.mojang.serialization.Codec;
import dev.latvian.mods.kubejs.event.KubeEvent;
import dev.stagelock.api.StageRequirement;
import dev.stagelock.api.restriction.BlockRestriction;
import dev.stagelock.api.restriction.DimensionRestriction;
import dev.stagelock.api.restriction.EntityRestriction;
import dev.stagelock.api.restriction.ItemMatcher;
import dev.stagelock.api.restriction.ItemRestriction;
import dev.stagelock.api.restriction.RecipeRestriction;
import dev.stagelock.api.restriction.RegistryMatcher;
import dev.stagelock.api.restriction.Restriction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * {@code StageLockEvents.restrictions(event => { ... })} - builds the same rules as datapack JSON.
 *
 * <pre>{@code
 * StageLockEvents.restrictions(event => {
 *   event.item('minecraft:diamond').requires('iron_age').hideName('Shiny Rock')
 *   event.item(['@mekanism']).except('mekanism:configurator').requires({any_of: ['tech', 'magic']}).only('use', 'place')
 *   event.recipe('minecraft:iron_pickaxe').requires('iron_age')
 *   event.recipeType('minecraft:smelting').requires('furnace_age')
 *   event.block('#c:ores/uranium').requires('nuclear')
 *   event.dimension('minecraft:the_nether').requires('nether').message('Too hot!')
 *   event.entity('minecraft:wither').requires('endgame').preventSpawn(128)
 *   event.add({type: 'stagelock:item', requires: 'x', items: 'minecraft:stick'})
 * })
 * }</pre>
 */
public final class RestrictionsKubeEvent implements KubeEvent {

    private final HolderLookup.Provider registries;
    private final List<Supplier<Restriction>> builders = new ArrayList<>();

    RestrictionsKubeEvent(HolderLookup.Provider registries) {
        this.registries = registries;
    }

    void collect(Map<ResourceLocation, Restriction> out) {
        for (int i = 0; i < builders.size(); i++) {
            out.put(ResourceLocation.fromNamespaceAndPath("kubejs", "rule_" + i), builders.get(i).get());
        }
    }

    /** Adds a rule written exactly like a datapack JSON file. */
    public void add(Object json) {
        Restriction rule = JsValues.parse(Restriction.CODEC, json, registries, "restriction");
        builders.add(() -> rule);
    }

    public ItemRule item(Object items) {
        ItemRule rule = new ItemRule(JsValues.parse(ItemMatcher.LIST_CODEC, items, registries, "items"));
        builders.add(rule::build);
        return rule;
    }

    public RecipeRule recipe(Object ids) {
        RecipeRule rule = new RecipeRule();
        rule.recipes = JsValues.parse(RecipeRestriction.RecipeIdMatcher.LIST_CODEC, ids, registries, "recipes");
        builders.add(rule::build);
        return rule;
    }

    public RecipeRule recipeOutput(Object items) {
        RecipeRule rule = new RecipeRule();
        rule.outputs = JsValues.parse(ItemMatcher.LIST_CODEC, items, registries, "outputs");
        builders.add(rule::build);
        return rule;
    }

    public RecipeRule recipeType(Object types) {
        RecipeRule rule = new RecipeRule();
        rule.types = JsValues.parse(Codec.either(ResourceLocation.CODEC.listOf(), ResourceLocation.CODEC)
                .xmap(e -> e.map(l -> l, List::of), com.mojang.datafixers.util.Either::<List<ResourceLocation>, ResourceLocation>left),
                types, registries, "recipe types");
        builders.add(rule::build);
        return rule;
    }

    public BlockRule block(Object blocks) {
        BlockRule rule = new BlockRule(JsValues.parse(RegistryMatcher.listCodec(Registries.BLOCK), blocks, registries, "blocks"));
        builders.add(rule::build);
        return rule;
    }

    public DimensionRule dimension(Object dimensions) {
        DimensionRule rule = new DimensionRule(JsValues.parse(Codec.either(ResourceLocation.CODEC.listOf(), ResourceLocation.CODEC)
                .xmap(e -> e.map(l -> l, List::of), com.mojang.datafixers.util.Either::<List<ResourceLocation>, ResourceLocation>left),
                dimensions, registries, "dimensions"));
        builders.add(rule::build);
        return rule;
    }

    public EntityRule entity(Object entities) {
        EntityRule rule = new EntityRule(JsValues.parse(RegistryMatcher.listCodec(Registries.ENTITY_TYPE), entities, registries, "entities"));
        builders.add(rule::build);
        return rule;
    }

    // ---------------------------------------------------------------- builders

    abstract class Rule<B extends Rule<B>> {
        StageRequirement requires;
        Optional<Component> message = Optional.empty();

        @SuppressWarnings("unchecked")
        private B self() {
            return (B) this;
        }

        /** {@code 'stage'}, {@code ['a', 'b']} (all of), {@code {any_of: [...]}}, {@code {not: ...}}. */
        public B requires(Object requirement) {
            this.requires = JsValues.parse(StageRequirement.CODEC, requirement, registries, "requirement");
            return self();
        }

        public B message(String text) {
            this.message = Optional.of(Component.literal(text));
            return self();
        }

        StageRequirement requirement() {
            if (requires == null) throw new IllegalArgumentException("Restriction is missing .requires(...)");
            return requires;
        }
    }

    public final class ItemRule extends Rule<ItemRule> {
        private final List<ItemMatcher> items;
        private List<ItemMatcher> except = List.of();
        private Set<String> blocked = Set.of("pickup", "use", "place", "attack", "mine", "equip", "hold", "craft", "viewer");
        private ItemRestriction.HoldMode hold = ItemRestriction.HoldMode.DROP;
        private boolean hideName = true;
        private Optional<Component> hiddenName = Optional.empty();
        private ItemRestriction.TooltipMode tooltip = ItemRestriction.TooltipMode.REPLACE;

        ItemRule(List<ItemMatcher> items) {
            this.items = items;
        }

        public ItemRule except(Object items) {
            this.except = JsValues.parse(ItemMatcher.LIST_CODEC, items, registries, "except");
            return this;
        }

        /** Block only these actions: pickup, use, place, attack, mine, equip, hold, craft, viewer. */
        public ItemRule only(String... actions) {
            this.blocked = normalize(actions);
            return this;
        }

        /** Stop blocking these actions. */
        public ItemRule allow(String... actions) {
            Set<String> remaining = new java.util.LinkedHashSet<>(blocked);
            remaining.removeAll(normalize(actions));
            this.blocked = remaining;
            return this;
        }

        /** What happens to the item in a locked player's inventory: allow, drop or destroy. */
        public ItemRule hold(String mode) {
            this.hold = ItemRestriction.HoldMode.valueOf(mode.toUpperCase(Locale.ROOT));
            return this;
        }

        public ItemRule hideName(String name) {
            this.hideName = true;
            this.hiddenName = Optional.of(Component.literal(name));
            return this;
        }

        public ItemRule showName() {
            this.hideName = false;
            return this;
        }

        /** replace, append or none. */
        public ItemRule tooltip(String mode) {
            this.tooltip = ItemRestriction.TooltipMode.valueOf(mode.toUpperCase(Locale.ROOT));
            return this;
        }

        Restriction build() {
            var actions = new ItemRestriction.Actions(blocked.contains("pickup"), blocked.contains("use"), blocked.contains("place"),
                    blocked.contains("attack"), blocked.contains("mine"), blocked.contains("equip"),
                    blocked.contains("hold") ? hold : ItemRestriction.HoldMode.ALLOW, blocked.contains("craft"),
                    blocked.contains("viewer") ? ItemRestriction.ViewerMode.HIDE : ItemRestriction.ViewerMode.SHOW);
            return new ItemRestriction(requirement(), items, except, actions, hideName, hiddenName, tooltip, message);
        }
    }

    public final class RecipeRule extends Rule<RecipeRule> {
        private List<RecipeRestriction.RecipeIdMatcher> recipes = List.of();
        private List<ItemMatcher> outputs = List.of();
        private List<ResourceLocation> types = List.of();
        private ItemRestriction.ViewerMode viewer = ItemRestriction.ViewerMode.HIDE;

        public RecipeRule showInViewers() {
            this.viewer = ItemRestriction.ViewerMode.SHOW;
            return this;
        }

        Restriction build() {
            return new RecipeRestriction(requirement(), recipes, outputs, types, viewer);
        }
    }

    public final class BlockRule extends Rule<BlockRule> {
        private final List<RegistryMatcher<net.minecraft.world.level.block.Block>> blocks;
        private List<RegistryMatcher<net.minecraft.world.level.block.Block>> except = List.of();
        private Set<String> blocked = Set.of("break", "interact", "place");
        private Optional<net.minecraft.world.level.block.Block> disguise = Optional.empty();

        BlockRule(List<RegistryMatcher<net.minecraft.world.level.block.Block>> blocks) {
            this.blocks = blocks;
        }

        public BlockRule except(Object blocks) {
            this.except = JsValues.parse(RegistryMatcher.listCodec(Registries.BLOCK), blocks, registries, "except");
            return this;
        }

        /** Block only these actions: break, interact, place, drops. */
        public BlockRule only(String... actions) {
            this.blocked = normalize(actions);
            return this;
        }

        public BlockRule allow(String... actions) {
            Set<String> remaining = new java.util.LinkedHashSet<>(blocked);
            remaining.removeAll(normalize(actions));
            this.blocked = remaining;
            return this;
        }

        /** Locked players see (and mine, and get drops of) this block instead. */
        public BlockRule disguiseAs(String block) {
            this.disguise = BuiltInRegistries.BLOCK.getOptional(ResourceLocation.parse(block));
            return this;
        }

        Restriction build() {
            return new BlockRestriction(requirement(), blocks, except, new BlockRestriction.Actions(
                    blocked.contains("break"), blocked.contains("interact"), blocked.contains("place"), blocked.contains("drops")),
                    disguise, message);
        }
    }

    public final class DimensionRule extends Rule<DimensionRule> {
        private final List<ResourceLocation> dimensions;

        DimensionRule(List<ResourceLocation> dimensions) {
            this.dimensions = dimensions;
        }

        Restriction build() {
            return new DimensionRestriction(requirement(), dimensions, message);
        }
    }

    public final class EntityRule extends Rule<EntityRule> {
        private final List<RegistryMatcher<net.minecraft.world.entity.EntityType<?>>> entities;
        private boolean attack = true;
        private boolean interact = true;
        private Optional<EntityRestriction.Spawn> spawn = Optional.empty();

        EntityRule(List<RegistryMatcher<net.minecraft.world.entity.EntityType<?>>> entities) {
            this.entities = entities;
        }

        public EntityRule allowAttack() {
            this.attack = false;
            return this;
        }

        public EntityRule allowInteract() {
            this.interact = false;
            return this;
        }

        /** Only spawns naturally if a player within {@code range} blocks meets the requirement. */
        public EntityRule preventSpawn(int range) {
            this.spawn = Optional.of(new EntityRestriction.Spawn(range, true));
            return this;
        }

        public EntityRule preventSpawn(int range, boolean allowSpawners) {
            this.spawn = Optional.of(new EntityRestriction.Spawn(range, allowSpawners));
            return this;
        }

        Restriction build() {
            return new EntityRestriction(requirement(), entities, new EntityRestriction.Actions(attack, interact, spawn), message);
        }
    }

    private static Set<String> normalize(String... actions) {
        return Arrays.stream(actions).map(a -> a.toLowerCase(Locale.ROOT)).collect(Collectors.toCollection(java.util.LinkedHashSet::new));
    }
}
