package dev.stagelock.api.restriction;

import com.mojang.datafixers.util.Either;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;

import java.util.List;

/**
 * Selects registry entries (blocks, entity types, fluids, enchantments) by id, {@code #tag} or {@code @modid}.
 */
public sealed interface RegistryMatcher<T> {

    boolean matches(Holder<T> holder);

    record Id<T>(ResourceLocation id) implements RegistryMatcher<T> {
        @Override
        public boolean matches(Holder<T> holder) {
            return holder.is(id);
        }

        @Override
        public String toString() {
            return id.toString();
        }
    }

    record Tag<T>(TagKey<T> tag) implements RegistryMatcher<T> {
        @Override
        public boolean matches(Holder<T> holder) {
            return holder.is(tag);
        }

        @Override
        public String toString() {
            return "#" + tag.location();
        }
    }

    record Mod<T>(String namespace) implements RegistryMatcher<T> {
        @Override
        public boolean matches(Holder<T> holder) {
            return holder.unwrapKey().map(k -> k.location().getNamespace().equals(namespace)).orElse(false);
        }

        @Override
        public String toString() {
            return "@" + namespace;
        }
    }

    static <T> Codec<RegistryMatcher<T>> codec(ResourceKey<? extends Registry<T>> registry) {
        return Codec.STRING.comapFlatMap(raw -> parse(registry, raw), RegistryMatcher::toString);
    }

    /** Accepts a single matcher or a list. */
    static <T> Codec<List<RegistryMatcher<T>>> listCodec(ResourceKey<? extends Registry<T>> registry) {
        Codec<RegistryMatcher<T>> single = codec(registry);
        return Codec.either(single.listOf(), single).xmap(e -> e.map(l -> l, List::of), Either::left);
    }

    static <T> boolean anyMatch(List<RegistryMatcher<T>> matchers, Holder<T> holder) {
        for (RegistryMatcher<T> matcher : matchers) {
            if (matcher.matches(holder)) return true;
        }
        return false;
    }

    static <T> DataResult<RegistryMatcher<T>> parse(ResourceKey<? extends Registry<T>> registry, String raw) {
        if (raw.startsWith("@")) {
            String namespace = raw.substring(1);
            return !namespace.isEmpty() && ResourceLocation.isValidNamespace(namespace)
                    ? DataResult.success(new Mod<>(namespace))
                    : DataResult.error(() -> "Invalid mod id: " + raw);
        }
        if (raw.startsWith("#")) {
            return ResourceLocation.read(raw.substring(1)).map(id -> new Tag<>(TagKey.create(registry, id)));
        }
        return ResourceLocation.read(raw).map(Id::new);
    }
}
