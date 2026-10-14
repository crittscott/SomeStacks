package com.github.crittscott.somestacks;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class FabricServerConfigPersistenceTest {
    @TempDir Path directory;

    @Test
    void completePolicyRoundTripsWithExistingJsonVocabulary() throws Exception {
        Path file = directory.resolve("world/serverconfig/somestacks-server.json");
        FabricServerConfig.Persistence persistence = new FabricServerConfig.Persistence(file);
        ServerConfig.Settings authored = new ServerConfig.Settings(12, false, true, false,
                List.of("example"), List.of("minecraft:stone"), 9, true, 2, List.of("minecraft"), List.of("minecraft:apple"));
        persistence.write(authored);
        assertEquals(authored, persistence.read(ServerConfig.Settings.defaults()));
        String json = Files.readString(file);
        assertTrue(json.contains("max_pile_height"));
        assertTrue(json.contains("required_permission_level"));
        assertTrue(json.contains("disable_items"));
        assertFalse(json.contains("ingot_tags"));
    }

    @Test
    void missingWorldAndMissingFieldsUseLoaderDefaults() throws Exception {
        Path file = directory.resolve("fabric/serverconfig/somestacks-server.json");
        FabricServerConfig.Persistence persistence = new FabricServerConfig.Persistence(file);
        ServerConfig.Settings defaults = ServerConfig.Settings.defaults();
        assertEquals(defaults, persistence.read(defaults));
        assertTrue(Files.exists(file));
        Files.writeString(file, "{}");
        assertEquals(defaults, persistence.read(defaults));
        assertEquals(List.of("evilcraft:broom_part"), defaults.disableItems());
    }

    @Test
    void numericPolicyBoundsArePreserved() throws Exception {
        Path file = directory.resolve("somestacks-server.json");
        Files.writeString(file, """
                {"piles": {"max_pile_height": 999},
                 "render_gallery": {"placements_per_tick": 0, "required_permission_level": -1}}
                """);
        ServerConfig.Settings settings = new FabricServerConfig.Persistence(file)
                .read(ServerConfig.Settings.defaults());
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
        ServerConfig.Settings defaults = ServerConfig.Settings.defaults();
        assertEquals(defaults, new FabricServerConfig.Persistence(file).read(defaults));
    }
}
