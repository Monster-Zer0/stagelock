package dev.stagelock;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import dev.stagelock.api.StageIds;
import dev.stagelock.api.StageRequirement;
import dev.stagelock.api.StageSet;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StageRequirementTest {

    private static StageRequirement parse(String json) {
        return StageRequirement.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString(json)).getOrThrow();
    }

    private static boolean parseFails(String json) {
        return StageRequirement.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString(json)).isError();
    }

    @Test
    void parsesAllForms() {
        assertEquals(new StageRequirement.Single("iron_age"), parse("\"iron_age\""));
        assertEquals(StageRequirement.allOf("a", "b"), parse("[\"a\", \"b\"]"));
        assertEquals(StageRequirement.allOf("a", "b"), parse("{\"all_of\": [\"a\", \"b\"]}"));
        assertEquals(StageRequirement.anyOf("a", "b"), parse("{\"any_of\": [\"a\", \"b\"]}"));
        assertEquals(new StageRequirement.Not(StageRequirement.of("a")), parse("{\"not\": \"a\"}"));
        assertEquals(StageRequirement.ALWAYS, parse("[]"));
    }

    @Test
    void normalizesCase() {
        assertEquals(StageRequirement.of("mypack:iron_age"), parse("\"MyPack:Iron_Age\""));
    }

    @Test
    void rejectsInvalid() {
        assertTrue(parseFails("\"has space\""));
        assertTrue(parseFails("\"" + "x".repeat(StageIds.MAX_LENGTH + 1) + "\""));
        assertTrue(parseFails("{}"));
        assertTrue(parseFails("{\"any_of\": [], \"not\": \"a\"}"));
        assertTrue(parseFails("42"));
    }

    @Test
    void evaluates() {
        StageSet stages = StageSet.of(Set.of("a", "c"));
        assertTrue(parse("\"a\"").test(stages));
        assertFalse(parse("[\"a\", \"b\"]").test(stages));
        assertTrue(parse("{\"any_of\": [\"b\", \"c\"]}").test(stages));
        assertTrue(parse("{\"not\": \"b\"}").test(stages));
        assertTrue(StageRequirement.ALWAYS.test(StageSet.EMPTY));
        assertTrue(parse("{\"any_of\": [[\"a\", \"c\"], \"z\"]}").test(stages));
    }

    @Test
    void reportsMissing() {
        StageSet stages = StageSet.of(Set.of("a"));
        assertEquals(Set.of("b"), parse("[\"a\", \"b\"]").missing(stages));
        assertEquals(Set.of("x", "y"), parse("{\"any_of\": [\"x\", \"y\"]}").missing(stages));
        assertEquals(Set.of(), parse("{\"any_of\": [\"a\", \"y\"]}").missing(stages));
    }

    @Test
    void roundTrips() {
        for (String json : List.of("\"a\"", "[\"a\",\"b\"]", "{\"any_of\":[\"a\",{\"not\":\"b\"}]}")) {
            StageRequirement requirement = parse(json);
            JsonElement encoded = StageRequirement.CODEC.encodeStart(JsonOps.INSTANCE, requirement).getOrThrow();
            assertEquals(requirement, StageRequirement.CODEC.parse(JsonOps.INSTANCE, encoded).getOrThrow());
        }
    }
}
