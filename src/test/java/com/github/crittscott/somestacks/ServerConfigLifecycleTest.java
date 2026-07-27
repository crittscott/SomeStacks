package com.github.crittscott.somestacks;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class ServerConfigLifecycleTest {
    @Test
    void tagUpdateBeforeServerConfigLoadWaitsWithoutChangingTheCache() {
        assertFalse(ServerConfig.SERVER_CONFIG.isLoaded());
        int generationBefore = ServerConfig.ingotGeneration();

        assertDoesNotThrow(() -> ServerConfig.onTagsUpdated(null));

        assertEquals(generationBefore, ServerConfig.ingotGeneration());
    }
}
