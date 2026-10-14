package com.github.crittscott.somestacks;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ServerConfigLifecycleTest {
    @AfterEach
    void clear() {
        ServerConfig.clear();
    }

    @Test
    void reloadRereadsAndPublishesTheWorldPolicy() {
        List<ServerConfig.Settings> reads = new ArrayList<>(List.of(withHeight(5), withHeight(9)));
        ServerConfig.start(() -> reads.remove(0), settings -> {});
        assertEquals(5, ServerConfig.maxPileHeight());

        assertTrue(ServerConfig.reload());

        assertEquals(9, ServerConfig.maxPileHeight());
        assertTrue(reads.isEmpty());
    }

    @Test
    void reloadWithoutARunningWorldDoesNothing() {
        assertFalse(ServerConfig.reload());
        assertEquals(ServerConfig.Settings.defaults(), ServerConfig.settings());
    }

    private static ServerConfig.Settings withHeight(int height) {
        ServerConfig.Settings defaults = ServerConfig.Settings.defaults();
        return new ServerConfig.Settings(height, defaults.enableStorageStackBlock(),
                defaults.enableSinglesStackBlock(), defaults.enableBarStackBlock(),
                defaults.disableMods(), defaults.disableItems(),
                defaults.renderGalleryPlacementsPerTick(), defaults.galleryEnabled(),
                defaults.galleryPermissionLevel(), defaults.genMods(), defaults.genItems());
    }
}
