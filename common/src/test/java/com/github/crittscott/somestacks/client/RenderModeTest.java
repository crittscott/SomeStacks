package com.github.crittscott.somestacks.client;

import com.github.crittscott.somestacks.renderconfig.RenderMode;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class RenderModeTest {
    @Test
    void idsAreUniqueAndRoundTrip() {
        Set<String> ids = Arrays.stream(RenderMode.values())
                .map(RenderMode::getId)
                .collect(Collectors.toSet());

        assertEquals(RenderMode.values().length, ids.size());
        for (RenderMode mode : RenderMode.values()) {
            assertEquals(mode, RenderMode.fromString(mode.getId()));
        }
    }

    @Test
    void parsingIsExact() {
        assertNull(RenderMode.fromString(""));
        assertNull(RenderMode.fromString("3D"));
        assertNull(RenderMode.fromString("three_d"));
        assertNull(RenderMode.fromString("missing"));
        assertNull(RenderMode.fromString(null));
    }
}
