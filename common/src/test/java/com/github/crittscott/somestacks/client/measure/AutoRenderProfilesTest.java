package com.github.crittscott.somestacks.client.measure;

import com.github.crittscott.somestacks.client.RenderMode;
import com.google.gson.JsonObject;
import net.minecraft.world.phys.AABB;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AutoRenderProfilesTest {
    private static final AABB CUBE = new AABB(0.0, 0.0, 0.0, 1.0, 1.0, 1.0);

    @Test
    void guiFlatModelWithProjectableQuadsUsesTwoD() {
        var result = new ModelMeasurement.Result(false, true, CUBE, null);

        assertEquals(RenderMode.TWO_D, AutoRenderProfiles.selectMode(result));
    }

    @Test
    void geometricallyFlatModelUsesTwoDDespiteGuiFlag() {
        var bounds = new AABB(0.0, 0.0, 0.0, 1.0, 1.0, 0.05);
        var result = new ModelMeasurement.Result(true, true, bounds, null);

        assertEquals(RenderMode.TWO_D, AutoRenderProfiles.selectMode(result));
    }

    @Test
    void volumetricModelUsesThreeD() {
        var result = new ModelMeasurement.Result(true, true, CUBE, null);

        assertEquals(RenderMode.THREE_D, AutoRenderProfiles.selectMode(result));
    }

    @Test
    void unavailableProjectionUsesThreeDEvenWhenGuiFlagIsFlat() {
        var result = new ModelMeasurement.Result(false, false, null, "no ordinary quads");

        assertEquals(RenderMode.THREE_D, AutoRenderProfiles.selectMode(result));
    }

    @Test
    void customRendererFallbackCannotSelectTwoDFromItsPlaceholder() {
        var result = new ModelMeasurement.Result(false, false, CUBE, "custom renderer is not measured");

        assertEquals(RenderMode.THREE_D, AutoRenderProfiles.selectMode(result));
    }

    @Test
    void onlyCurrentCacheFormatIsAccepted() {
        JsonObject unversioned = new JsonObject();
        JsonObject old = new JsonObject();
        old.addProperty("format", AutoRenderProfiles.CACHE_FORMAT_VERSION - 1);
        JsonObject current = new JsonObject();
        current.addProperty("format", AutoRenderProfiles.CACHE_FORMAT_VERSION);

        assertFalse(AutoRenderProfiles.isCurrentCacheFormat(unversioned));
        assertFalse(AutoRenderProfiles.isCurrentCacheFormat(old));
        assertTrue(AutoRenderProfiles.isCurrentCacheFormat(current));
    }
}
