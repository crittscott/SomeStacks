package com.github.crittscott.somestacks.resource;

import com.github.crittscott.somestacks.client.RenderMode;
import com.github.crittscott.somestacks.util.OverrideJsonCodec;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BundledResourceTest {
    private static final Path RESOURCES = Path.of("src", "main", "resources");
    private static final Path OVERRIDES = RESOURCES.resolve(
            Path.of("assets", "somestacks", "item_render_overrides"));
    private static final Set<String> ENTRY_FIELDS = Set.of("mode", "scale", "offset");
    private static final Set<String> MODES = Set.of(
            java.util.Arrays.stream(RenderMode.values())
                    .map(RenderMode::getId)
                    .toArray(String[]::new));

    @Test
    void everyJsonResourceParses() throws IOException {
        try (Stream<Path> paths = Files.walk(RESOURCES)) {
            for (Path path : paths.filter(p -> p.toString().endsWith(".json")).toList()) {
                assertDoesNotThrow(
                        () -> JsonParser.parseString(Files.readString(path)),
                        path.toString());
            }
        }
    }

    @Test
    void everyBundledOverrideUsesTheSharedSchema() throws IOException {
        int fileCount = 0;
        int entryCount = 0;

        try (Stream<Path> paths = Files.list(OVERRIDES)) {
            for (Path path : paths.filter(p -> p.toString().endsWith(".json")).sorted().toList()) {
                fileCount++;
                JsonObject root = JsonParser.parseString(Files.readString(path)).getAsJsonObject();

                for (var entry : root.entrySet()) {
                    entryCount++;
                    assertDoesNotThrow(() -> new ResourceLocation(entry.getKey()),
                            path + ": " + entry.getKey());
                    assertTrue(entry.getValue().isJsonObject(),
                            path + ": " + entry.getKey() + " is not an object");
                    validateEntry(path, entry.getKey(), entry.getValue().getAsJsonObject());
                }
            }
        }

        assertTrue(fileCount > 0);
        assertTrue(entryCount > 0);
    }

    /**
     * The corpus is loaded by file name: a file naming a namespace the client does not have is
     * skipped unread, so an entry filed under the wrong name would never be seen.
     */
    @Test
    void everyBundledOverrideFileIsNamedForTheNamespaceItCovers() throws IOException {
        try (Stream<Path> paths = Files.list(OVERRIDES)) {
            for (Path path : paths.filter(p -> p.toString().endsWith(".json")).sorted().toList()) {
                String fileName = path.getFileName().toString();
                String namespace = fileName.substring(0, fileName.length() - ".json".length());
                JsonObject root = JsonParser.parseString(Files.readString(path)).getAsJsonObject();

                for (var entry : root.entrySet()) {
                    assertEquals(namespace, new ResourceLocation(entry.getKey()).getNamespace(),
                            path + ": " + entry.getKey() + " is filed under the wrong namespace");
                }
            }
        }
    }

    @Test
    void bundledSoundDataHasEveryStackAction() throws IOException {
        Path path = RESOURCES.resolve(
                Path.of("data", "somestacks", "somestacks_sounds", "default.json"));
        JsonObject root = JsonParser.parseString(Files.readString(path)).getAsJsonObject();

        for (String stack : Set.of(
                "storage_stack_block", "singles_stack_block", "bar_stack_block")) {
            assertTrue(root.has(stack), stack);
            JsonObject actions = root.getAsJsonObject(stack);
            assertTrue(actions.has("deposit"), stack + ".deposit");
            assertTrue(actions.has("extract"), stack + ".extract");
            assertEquals(2, actions.size(), stack);
            assertFalse(actions.get("deposit").getAsString().isBlank());
            assertFalse(actions.get("extract").getAsString().isBlank());
        }
    }

    @Test
    void bundledIngotTagIsAppendOnlyAndNamesValues() throws IOException {
        Path path = RESOURCES.resolve(
                Path.of("data", "somestacks", "tags", "items", "ingots.json"));
        JsonObject root = JsonParser.parseString(Files.readString(path)).getAsJsonObject();

        assertFalse(root.get("replace").getAsBoolean());
        JsonArray values = root.getAsJsonArray("values");
        assertTrue(values.size() > 0);
        for (JsonElement value : values) {
            assertTrue(value.isJsonPrimitive());
            assertFalse(value.getAsString().isBlank());
        }
    }

    private static void validateEntry(Path path, String itemId, JsonObject entry) {
        assertTrue(ENTRY_FIELDS.containsAll(entry.keySet()),
                path + ": " + itemId + " has unknown fields " + entry.keySet());

        if (entry.has("mode")) {
            assertTrue(entry.get("mode").isJsonPrimitive());
            assertTrue(MODES.contains(entry.get("mode").getAsString()),
                    path + ": " + itemId + " has invalid mode");
        }

        if (entry.has("scale")) {
            float scale = entry.get("scale").getAsFloat();
            assertTrue(OverrideJsonCodec.inRange(
                            scale, OverrideJsonCodec.MIN_SCALE, OverrideJsonCodec.MAX_SCALE),
                    path + ": " + itemId + " has invalid scale");
        }

        if (entry.has("offset")) {
            JsonArray offset = entry.getAsJsonArray("offset");
            assertEquals(3, offset.size(), path + ": " + itemId + " offset length");
            for (JsonElement component : offset) {
                float value = component.getAsFloat();
                assertTrue(OverrideJsonCodec.inRange(
                                value, OverrideJsonCodec.MIN_OFFSET, OverrideJsonCodec.MAX_OFFSET),
                        path + ": " + itemId + " has invalid offset");
            }
        }
    }
}
