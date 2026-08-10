package com.github.crittscott.somestacks;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;

class ServerConfigLifecycleTest {
    @Test
    void tagRebakeBeforeConfigLoadWaitsWithoutChangingTheCache() {
        int generationBefore = ServerConfig.ingotGeneration();

        assertDoesNotThrow(ServerConfig::rebakeIngotTags);

        assertEquals(generationBefore, ServerConfig.ingotGeneration());
    }
}
