package com.github.crittscott.somestacks.client;

import com.github.crittscott.somestacks.client.BarTextureStore.BarTextureData;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The bar mapping parse. Whether a colour is to be computed is carried apart from the colour
 * itself, so that white — the colour a pack author names to decline tinting — is an answer rather
 * than a request.
 */
class BarTextureParseTest {
    private static JsonElement json(String text) {
        return JsonParser.parseString(text);
    }

    /** Parses a mapping that is expected to be usable. */
    private static BarTextureData parsed(String text) {
        BarTextureData data = BarTextureStore.parseEntry(json(text));
        assertNotNull(data, text);
        return data;
    }

    @Test
    void anExplicitWhiteTintIsAnAnswerRatherThanARequestToComputeOne() {
        for (String tint : new String[] {"#FFFFFF", "#FFFFFFFF", "FFFFFF"}) {
            BarTextureData data = parsed(
                    "{\"texture\": \"somestacks:block/bar\", \"tint\": \"" + tint + "\"}");

            assertFalse(data.autoTint(), tint);
            assertEquals(BarTextureData.WHITE, data.color(), tint);
            assertEquals(255, data.red(), tint);
            assertEquals(255, data.alpha(), tint);
        }
    }

    @Test
    void aMappingThatNamesNoTintAsksForOne() {
        BarTextureData object = parsed("{\"texture\": \"somestacks:block/bar\"}");
        assertTrue(object.autoTint());
        assertEquals("somestacks:block/bar", object.texture().toString());

        BarTextureData bareTexture = parsed("\"somestacks:block/bar\"");
        assertTrue(bareTexture.autoTint());
        assertEquals("somestacks:block/bar", bareTexture.texture().toString());
    }

    @Test
    void anAuthoredTintIsCarriedWholeAndAnUnusableMappingIsRejected() {
        BarTextureData rgb = parsed("{\"tint\": \"#7F0000\"}");
        assertFalse(rgb.autoTint());
        assertEquals(0xFF7F0000, rgb.color());
        assertEquals(0x7F, rgb.red());
        assertEquals(0xFF, rgb.alpha(), "a six digit tint is fully opaque");

        BarTextureData argb = parsed("{\"tint\": \"#807F0000\"}");
        assertEquals(0x80, argb.alpha());

        assertNull(BarTextureStore.parseEntry(json("42")), "neither a string nor an object");
    }
}
