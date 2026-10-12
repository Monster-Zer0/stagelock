package dev.stagelock.api;

import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.MapLike;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

/**
 * A boolean expression over stages.
 * <p>
 * JSON forms:
 * <ul>
 *     <li>{@code "iron_age"} - the stage must be present</li>
 *     <li>{@code ["a", "b"]} - all of the listed requirements ({@code []} always passes)</li>
 *     <li>{@code {"all_of": [...]}} / {@code {"any_of": [...]}} / {@code {"not": ...}}</li>
 * </ul>
 */
public sealed interface StageRequirement {

    StageRequirement ALWAYS = new AllOf(List.of());

    Codec<StageRequirement> CODEC = Codec.recursive("StageRequirement", StageRequirement::createCodec);

    StreamCodec<ByteBuf, StageRequirement> STREAM_CODEC = ByteBufCodecs.fromCodec(CODEC);

    boolean test(Predicate<String> has);

    default boolean test(StageSet stages) {
        return test(stages::has);
    }

    /** Collects the stages that are not satisfied, used for "Requires: ..." messages. */
    void collectMissing(Predicate<String> has, Collection<String> out);

    /** Collects every stage this requirement refers to. */
    void collectStages(Collection<String> out);

    default Set<String> missing(StageSet stages) {
        Set<String> out = new LinkedHashSet<>();
        collectMissing(stages::has, out);
        return out;
    }

    default Set<String> stages() {
        Set<String> out = new LinkedHashSet<>();
        collectStages(out);
        return out;
    }

    static StageRequirement of(String stage) {
        return new Single(stage);
    }

    static StageRequirement allOf(String... stages) {
        return new AllOf(java.util.Arrays.stream(stages).<StageRequirement>map(Single::new).toList());
    }

    static StageRequirement anyOf(String... stages) {
        return new AnyOf(java.util.Arrays.stream(stages).<StageRequirement>map(Single::new).toList());
    }

    record Single(String stage) implements StageRequirement {
        public Single {
            stage = StageIds.normalize(stage);
        }

        @Override
        public boolean test(Predicate<String> has) {
            return has.test(stage);
        }

        @Override
        public void collectMissing(Predicate<String> has, Collection<String> out) {
            if (!has.test(stage)) out.add(stage);
        }

        @Override
        public void collectStages(Collection<String> out) {
            out.add(stage);
        }
    }

    record AllOf(List<StageRequirement> children) implements StageRequirement {
        public AllOf {
            children = List.copyOf(children);
        }

        @Override
        public boolean test(Predicate<String> has) {
            for (StageRequirement child : children) {
                if (!child.test(has)) return false;
            }
            return true;
        }

        @Override
        public void collectMissing(Predicate<String> has, Collection<String> out) {
            for (StageRequirement child : children) child.collectMissing(has, out);
        }

        @Override
        public void collectStages(Collection<String> out) {
            for (StageRequirement child : children) child.collectStages(out);
        }
    }

    record AnyOf(List<StageRequirement> children) implements StageRequirement {
        public AnyOf {
            children = List.copyOf(children);
        }

        @Override
        public boolean test(Predicate<String> has) {
            for (StageRequirement child : children) {
                if (child.test(has)) return true;
            }
            return children.isEmpty();
        }

        @Override
        public void collectMissing(Predicate<String> has, Collection<String> out) {
            if (!test(has)) {
                for (StageRequirement child : children) child.collectMissing(has, out);
            }
        }

        @Override
        public void collectStages(Collection<String> out) {
            for (StageRequirement child : children) child.collectStages(out);
        }
    }

    record Not(StageRequirement child) implements StageRequirement {
        @Override
        public boolean test(Predicate<String> has) {
            return !child.test(has);
        }

        @Override
        public void collectMissing(Predicate<String> has, Collection<String> out) {
            if (!test(has)) {
                List<String> present = new ArrayList<>();
                child.collectStages(present);
                present.stream().filter(has).map(s -> "!" + s).forEach(out::add);
            }
        }

        @Override
        public void collectStages(Collection<String> out) {
            child.collectStages(out);
        }
    }

    private static Codec<StageRequirement> createCodec(Codec<StageRequirement> self) {
        Codec<List<StageRequirement>> list = self.listOf();
        return new Codec<>() {
            @Override
            public <T> DataResult<Pair<StageRequirement, T>> decode(DynamicOps<T> ops, T input) {
                DataResult<String> string = ops.getStringValue(input);
                if (string.isSuccess()) {
                    String id = string.getOrThrow();
                    return StageIds.validate(id).map(s -> Pair.of(new Single(s), input));
                }
                if (ops.getStream(input).isSuccess()) {
                    return list.decode(ops, input).map(p -> p.mapFirst(AllOf::new));
                }
                DataResult<MapLike<T>> map = ops.getMap(input);
                if (map.isError()) {
                    return DataResult.error(() -> "Stage requirement must be a string, a list or an object: " + input);
                }
                MapLike<T> m = map.getOrThrow();
                T allOf = m.get("all_of");
                T anyOf = m.get("any_of");
                T not = m.get("not");
                int present = (allOf != null ? 1 : 0) + (anyOf != null ? 1 : 0) + (not != null ? 1 : 0);
                if (present != 1) {
                    return DataResult.error(() -> "Stage requirement object must have exactly one of all_of, any_of, not: " + input);
                }
                if (allOf != null) return list.parse(ops, allOf).map(l -> Pair.of(new AllOf(l), input));
                if (anyOf != null) return list.parse(ops, anyOf).map(l -> Pair.of(new AnyOf(l), input));
                return self.parse(ops, not).map(r -> Pair.of(new Not(r), input));
            }

            @Override
            public <T> DataResult<T> encode(StageRequirement input, DynamicOps<T> ops, T prefix) {
                return switch (input) {
                    case Single single -> DataResult.success(ops.createString(single.stage()));
                    case AllOf allOf -> list.encode(allOf.children(), ops, prefix);
                    case AnyOf anyOf -> list.encodeStart(ops, anyOf.children())
                            .map(v -> ops.createMap(java.util.Map.of(ops.createString("any_of"), v)));
                    case Not n -> self.encodeStart(ops, n.child())
                            .map(v -> ops.createMap(java.util.Map.of(ops.createString("not"), v)));
                };
            }
        };
    }
}
