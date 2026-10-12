package dev.stagelock.api.restriction;

import com.mojang.datafixers.util.Either;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import net.minecraft.advancements.critereon.ItemPredicate;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.function.Consumer;

/**
 * Selects items. JSON forms:
 * <ul>
 *     <li>{@code "minecraft:diamond"} - one item</li>
 *     <li>{@code "#c:gems"} - an item tag</li>
 *     <li>{@code "@mekanism"} - every item of a mod</li>
 *     <li>{@code {"items": ..., "components": {...}}} - a vanilla item predicate (component/NBT matching)</li>
 * </ul>
 */
public sealed interface ItemMatcher {

    Codec<ItemMatcher> CODEC = Codec.either(Codec.STRING.flatXmap(ItemMatcher::parse, ItemMatcher::asString), ItemPredicate.CODEC)
            .xmap(either -> either.map(m -> m, Predicate::new),
                    matcher -> matcher instanceof Predicate p ? Either.right(p.predicate()) : Either.left(matcher));

    /** Accepts a single matcher or a list. */
    Codec<List<ItemMatcher>> LIST_CODEC = Codec.either(CODEC.listOf(), CODEC)
            .xmap(either -> either.map(l -> l, List::of), list -> list.size() == 1 ? Either.right(list.getFirst()) : Either.left(list));

    boolean matches(ItemStack stack);

    /**
     * Feeds every item this matcher can possibly match to {@code out}, for indexing.
     * Returns {@code false} if the matcher can match any item (it must then be tested against everything).
     */
    boolean collectCandidates(Consumer<Item> out);

    record Id(ResourceLocation id) implements ItemMatcher {
        @Override
        public boolean matches(ItemStack stack) {
            return stack.getItemHolder().is(id);
        }

        @Override
        public boolean collectCandidates(Consumer<Item> out) {
            BuiltInRegistries.ITEM.getOptional(id).ifPresent(out);
            return true;
        }
    }

    record Tag(TagKey<Item> tag) implements ItemMatcher {
        @Override
        public boolean matches(ItemStack stack) {
            return stack.is(tag);
        }

        @Override
        public boolean collectCandidates(Consumer<Item> out) {
            for (Holder<Item> holder : BuiltInRegistries.ITEM.getTagOrEmpty(tag)) out.accept(holder.value());
            return true;
        }
    }

    record Mod(String namespace) implements ItemMatcher {
        @Override
        public boolean matches(ItemStack stack) {
            return BuiltInRegistries.ITEM.getKey(stack.getItem()).getNamespace().equals(namespace);
        }

        @Override
        public boolean collectCandidates(Consumer<Item> out) {
            for (var entry : BuiltInRegistries.ITEM.entrySet()) {
                if (entry.getKey().location().getNamespace().equals(namespace)) out.accept(entry.getValue());
            }
            return true;
        }
    }

    record Predicate(ItemPredicate predicate) implements ItemMatcher {
        @Override
        public boolean matches(ItemStack stack) {
            return predicate.test(stack);
        }

        @Override
        public boolean collectCandidates(Consumer<Item> out) {
            if (predicate.items().isEmpty()) return false;
            for (Holder<Item> holder : predicate.items().get()) out.accept(holder.value());
            return true;
        }
    }

    /** Matches stacks carrying one of the enchantments. Built from enchantment rules; has no JSON form of its own. */
    record Enchanted(List<RegistryMatcher<net.minecraft.world.item.enchantment.Enchantment>> enchantments, boolean checkBooks) implements ItemMatcher {
        @Override
        public boolean matches(ItemStack stack) {
            if (matchesAny(stack.getEnchantments())) return true;
            return checkBooks && matchesAny(stack.getOrDefault(net.minecraft.core.component.DataComponents.STORED_ENCHANTMENTS,
                    net.minecraft.world.item.enchantment.ItemEnchantments.EMPTY));
        }

        private boolean matchesAny(net.minecraft.world.item.enchantment.ItemEnchantments present) {
            for (var holder : present.keySet()) {
                if (RegistryMatcher.anyMatch(enchantments, holder)) return true;
            }
            return false;
        }

        @Override
        public boolean collectCandidates(Consumer<Item> out) {
            return false;
        }
    }

    private static DataResult<ItemMatcher> parse(String raw) {
        if (raw.startsWith("@")) {
            String namespace = raw.substring(1);
            return ResourceLocation.isValidNamespace(namespace) && !namespace.isEmpty()
                    ? DataResult.success(new Mod(namespace))
                    : DataResult.error(() -> "Invalid mod id: " + raw);
        }
        if (raw.startsWith("#")) {
            return ResourceLocation.read(raw.substring(1)).map(id -> new Tag(TagKey.create(Registries.ITEM, id)));
        }
        return ResourceLocation.read(raw).map(Id::new);
    }

    private static DataResult<String> asString(ItemMatcher matcher) {
        return switch (matcher) {
            case Id id -> DataResult.success(id.id().toString());
            case Tag tag -> DataResult.success("#" + tag.tag().location());
            case Mod mod -> DataResult.success("@" + mod.namespace());
            case Predicate p -> DataResult.error(() -> "Predicate matchers have no string form");
            case Enchanted e -> DataResult.error(() -> "Enchantment matchers have no string form");
        };
    }

    static ItemMatcher of(String raw) {
        return parse(raw).getOrThrow(IllegalArgumentException::new);
    }

    static ItemMatcher of(HolderSet<Item> items) {
        return new Predicate(new ItemPredicate(java.util.Optional.of(items),
                net.minecraft.advancements.critereon.MinMaxBounds.Ints.ANY,
                net.minecraft.core.component.DataComponentPredicate.EMPTY, java.util.Map.of()));
    }
}
