package dev.stagelock.compat.kubejs;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.mojang.serialization.Codec;
import com.mojang.serialization.JsonOps;
import net.minecraft.core.HolderLookup;
import net.minecraft.resources.RegistryOps;

import java.util.Map;

/**
 * Converts script values (strings, numbers, JS arrays and objects, which Rhino exposes as Lists and
 * Maps) to JSON, so every builder goes through the same codecs as datapack files.
 */
final class JsValues {

    private JsValues() {
    }

    static JsonElement toJson(Object value) {
        if (value == null) return JsonNull.INSTANCE;
        if (value instanceof JsonElement json) return json;
        if (value instanceof CharSequence text) return new JsonPrimitive(text.toString());
        if (value instanceof Boolean bool) return new JsonPrimitive(bool);
        if (value instanceof Number number) {
            double d = number.doubleValue();
            return d == Math.rint(d) && !Double.isInfinite(d) ? new JsonPrimitive((long) d) : new JsonPrimitive(d);
        }
        if (value instanceof Map<?, ?> map) {
            JsonObject object = new JsonObject();
            map.forEach((k, v) -> object.add(String.valueOf(k), toJson(v)));
            return object;
        }
        if (value instanceof Iterable<?> list) {
            JsonArray array = new JsonArray();
            list.forEach(v -> array.add(toJson(v)));
            return array;
        }
        if (value.getClass().isArray() && value instanceof Object[] items) {
            JsonArray array = new JsonArray();
            for (Object item : items) array.add(toJson(item));
            return array;
        }
        return new JsonPrimitive(value.toString());
    }

    static <T> T parse(Codec<T> codec, Object value, HolderLookup.Provider registries, String what) {
        return codec.parse(RegistryOps.create(JsonOps.INSTANCE, registries), toJson(value))
                .getOrThrow(error -> new IllegalArgumentException("Invalid " + what + ": " + error));
    }
}
