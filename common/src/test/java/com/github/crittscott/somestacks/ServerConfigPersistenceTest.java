package com.github.crittscott.somestacks;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ServerConfigPersistenceTest {
    @TempDir Path directory;

    @Test
    void settingsCopyListsAndReadersCannotEditThem() {
        List<String> mods = new ArrayList<>(List.of("example"));
        ServerConfig.Settings settings = new ServerConfig.Settings(8, true, true, true,
                mods, List.of(), List.of("forge:ingots*", "somestacks:ingots"),
                64, false, 3, List.of(), List.of());
        mods.clear();
        assertEquals(List.of("example"), settings.disableMods());
        assertThrows(UnsupportedOperationException.class, () -> settings.disableMods().add("other"));
        assertThrows(UnsupportedOperationException.class, () -> settings.ingotTags().clear());
    }

    @Test
    void completePolicyRoundTripsWithExistingJsonVocabulary() throws Exception {
        Path file = directory.resolve("world/serverconfig/somestacks-server.json");
        ServerConfig.Persistence persistence = new ServerConfig.Persistence(file);
        ServerConfig.Settings authored = new ServerConfig.Settings(12, false, true, false,
                List.of("example"), List.of("minecraft:stone"), List.of("custom:ingots*", "somestacks:ingots"),
                9, true, 2, List.of("minecraft"), List.of("minecraft:apple"));
        persistence.write(authored);
        assertEquals(authored, persistence.read(ServerConfig.Settings.defaults(true)));
        String json = Files.readString(file);
        assertTrue(json.contains("max_pile_height"));
        assertTrue(json.contains("required_permission_level"));
        assertTrue(json.contains("disable_items"));
        assertTrue(json.contains("custom:ingots*"));
    }

    @Test
    void missingWorldAndMissingFieldsUseLoaderDefaults() throws Exception {
        Path file = directory.resolve("fabric/serverconfig/somestacks-server.json");
        ServerConfig.Persistence persistence = new ServerConfig.Persistence(file);
        ServerConfig.Settings defaults = ServerConfig.Settings.defaults(true);
        assertEquals(defaults, persistence.read(defaults));
        assertTrue(Files.exists(file));
        Files.writeString(file, "{}");
        assertEquals(defaults, persistence.read(defaults));
        assertEquals(List.of("c:ingots*", "somestacks:ingots"), defaults.ingotTags());
        assertEquals(List.of("forge:ingots*", "somestacks:ingots"),
                ServerConfig.Settings.defaults(false).ingotTags());
    }

    @Test
    void numericPolicyBoundsArePreserved() throws Exception {
        Path file = directory.resolve("somestacks-server.json");
        Files.writeString(file, """
                {"piles": {"max_pile_height": 999},
                 "render_gallery": {"placements_per_tick": 0, "required_permission_level": -1}}
                """);
        ServerConfig.Settings settings = new ServerConfig.Persistence(file)
                .read(ServerConfig.Settings.defaults(false));
        assertEquals(64, settings.maxPileHeight());
        assertEquals(1, settings.renderGalleryPlacementsPerTick());
        assertEquals(0, settings.galleryPermissionLevel());
    }

    @Test
    void invalidPolicyDoesNotPublishPartialSettings() throws Exception {
        Path file = directory.resolve("somestacks-server.json");
        Files.writeString(file, """
                {"piles": {"max_pile_height": 2},
                 "compatibility": {"disable_items": [{}]}}
                """);
        ServerConfig.Settings defaults = ServerConfig.Settings.defaults(false);
        assertEquals(defaults, new ServerConfig.Persistence(file).read(defaults));
    }
}
