package com.github.crittscott.somestacks.util;

import com.github.crittscott.somestacks.renderconfig.ItemRenderConfig;
import com.github.crittscott.somestacks.renderconfig.OverrideJsonCodec;
import com.github.crittscott.somestacks.renderconfig.RenderMode;
import com.github.crittscott.somestacks.renderconfig.RenderOffset;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OverrideJsonCodecTest {

    @Test
    void rangesAreInclusiveAndRejectNonFiniteValues() {
        assertTrue(OverrideJsonCodec.inRange(0.01f, 0.01f, 20.0f));
        assertTrue(OverrideJsonCodec.inRange(20.0f, 0.01f, 20.0f));
        assertFalse(OverrideJsonCodec.inRange(0.009f, 0.01f, 20.0f));
        assertFalse(OverrideJsonCodec.inRange(20.001f, 0.01f, 20.0f));
        assertFalse(OverrideJsonCodec.inRange(Float.NaN, 0.01f, 20.0f));
        assertFalse(OverrideJsonCodec.inRange(Float.POSITIVE_INFINITY, 0.01f, 20.0f));
        assertFalse(OverrideJsonCodec.inRange(Float.NEGATIVE_INFINITY, 0.01f, 20.0f));
    }

    @Test
    void parsesCompleteEntry() {
        JsonObject json = object("""
                {
                  "mode": "gui",
                  "scale": 0.75,
                  "offset": [0.1, -0.2, 0.3]
                }
                """);

        ItemRenderConfig config = OverrideJsonCodec.parseEntry("test:item", json);

        assertEquals(RenderMode.GUI, config.mode());
        assertEquals(0.75f, config.scale());
        assertEquals(new RenderOffset(0.1f, -0.2f, 0.3f), config.offset());
    }

    @Test
    void preservesIndependentlyValidFields() {
        JsonObject json = object("""
                {
                  "mode": "not-a-mode",
                  "scale": 2.0,
                  "offset": [0.0, 4.0, 0.0]
                }
                """);

        ItemRenderConfig config = OverrideJsonCodec.parseEntry("test:item", json);

        assertNull(config.mode());
        assertEquals(2.0f, config.scale());
        assertNull(config.offset());
    }

    @Test
    void rejectsOffsetWithWrongLength() {
        ItemRenderConfig config = OverrideJsonCodec.parseEntry(
                "test:item", object("{\"offset\": [0.0, 0.0]}"));

        assertNull(config);
    }

    @Test
    void rejectsEntryWithNoSuccessfullyParsedField() {
        ItemRenderConfig config = OverrideJsonCodec.parseEntry(
                "test:item", object("{\"mode\": \"wrong\", \"scale\": 100.0}"));

        assertNull(config);
    }

    @Test
    void malformedEntryDoesNotDiscardValidEntries() {
        JsonObject root = object("""
                {
                  "test:good": {"mode": "2d"},
                  "not a resource location": {"mode": "gui"},
                  "test:not_an_object": 4
                }
                """);

        Map<ResourceLocation, ItemRenderConfig> parsed =
                OverrideJsonCodec.parse(root, "test");

        assertEquals(1, parsed.size());
        assertEquals(RenderMode.TWO_D,
                parsed.get(new ResourceLocation("test", "good")).mode());
    }

    @Test
    void serializationSortsEntriesByItemId() {
        Map<ResourceLocation, ItemRenderConfig> entries = new LinkedHashMap<>();
        entries.put(new ResourceLocation("test", "z"),
                new ItemRenderConfig(RenderMode.GUI, null, null));
        entries.put(new ResourceLocation("test", "a"),
                new ItemRenderConfig(RenderMode.TWO_D, null, null));

        JsonObject json = OverrideJsonCodec.toJson(entries);

        assertEquals(
                java.util.List.of("test:a", "test:z"),
                java.util.List.copyOf(json.keySet()));
    }

    @Test
    void validValuesRoundTrip() {
        ResourceLocation id = new ResourceLocation("test", "item");
        Map<ResourceLocation, ItemRenderConfig> original = Map.of(
                id,
                new ItemRenderConfig(
                        RenderMode.THREE_D,
                        1.25f,
                        new RenderOffset(-0.25f, 0.5f, 0.75f)));

        Map<ResourceLocation, ItemRenderConfig> parsed =
                OverrideJsonCodec.parse(OverrideJsonCodec.toJson(original), "round-trip");
        ItemRenderConfig result = parsed.get(id);

        assertEquals(RenderMode.THREE_D, result.mode());
        assertEquals(1.25f, result.scale());
        assertEquals(new RenderOffset(-0.25f, 0.5f, 0.75f), result.offset());
    }

    @Test
    void synchronizedOffsetsRejectNonFiniteAndOutOfRangeComponents() {
        for (RenderOffset offset : java.util.List.of(
                new RenderOffset(Float.NaN, 0, 0),
                new RenderOffset(0, Float.POSITIVE_INFINITY, 0),
                new RenderOffset(0, 0, 1.01f))) {
            ItemRenderConfig sanitized = OverrideJsonCodec.sanitize(
                    new ItemRenderConfig(RenderMode.GUI, 1.0f, offset));
            assertNull(sanitized.offset());
            assertEquals(RenderMode.GUI, sanitized.mode());
            assertEquals(1.0f, sanitized.scale());
        }
        RenderOffset boundary = new RenderOffset(-1, 1, 0);
        assertEquals(boundary, OverrideJsonCodec.sanitize(
                new ItemRenderConfig(null, null, boundary)).offset());
    }

    private static JsonObject object(String json) {
        return JsonParser.parseString(json).getAsJsonObject();
    }
}
