package com.github.crittscott.somestacks.client;

import com.github.crittscott.somestacks.PlatformServices;
import com.github.crittscott.somestacks.client.measure.AutoRenderProfiles;
import com.github.crittscott.somestacks.network.ConfigSyncPkt;
import com.github.crittscott.somestacks.server.EditAuthority;
import com.github.crittscott.somestacks.renderconfig.ItemRenderConfig;
import com.github.crittscott.somestacks.renderconfig.RenderMode;
import com.github.crittscott.somestacks.renderconfig.RenderOffset;
import com.github.crittscott.somestacks.renderconfig.RenderProfile;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.lang.reflect.Field;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Reload and packet/edit entry points must release profiles derived from previous state. */
class RenderCacheInvalidationTest {
    @TempDir Path configDirectory;
    private Object previousBackend;
    private boolean previousUserFileLoaded;
    private boolean overrideStateInitialized;
    private final Map<String, Map<Object, Object>> previousMaps = new HashMap<>();
    private static final ResourceLocation ID = new ResourceLocation("test", "item");
    private static final ItemRenderConfig CONFIG =
            new ItemRenderConfig(RenderMode.GUI, 1.0f, RenderOffset.ZERO);

    private static Field field(Class<?> owner, String name) throws ReflectiveOperationException {
        Field field = owner.getDeclaredField(name);
        field.setAccessible(true);
        return field;
    }

    @SuppressWarnings("unchecked")
    private static Map<Object, Object> map(Class<?> owner, String name) throws ReflectiveOperationException {
        return (Map<Object, Object>) field(owner, name).get(null);
    }

    @BeforeEach
    void isolateOverrideState() throws ReflectiveOperationException {
        Field backend = field(PlatformServices.class, "backend");
        previousBackend = backend.get(null);
        backend.set(null, new PlatformServices.Backend() {
            @Override
            public Path configFolder() {
                return configDirectory;
            }

            @Override
            public String modVersion(String namespace) {
                throw new UnsupportedOperationException("modVersion");
            }

            @Override
            public void sendConfig(ServerPlayer player, ConfigSyncPkt packet) {
                throw new UnsupportedOperationException("sendConfig");
            }

            @Override
            public EditAuthority editAuthority() {
                throw new UnsupportedOperationException("editAuthority");
            }
        });
        previousUserFileLoaded = field(ItemRenderOverrides.class, "userFileLoaded").getBoolean(null);
        overrideStateInitialized = true;
        field(ItemRenderOverrides.class, "userFileLoaded").setBoolean(null, true);
        for (String name : new String[]{"CONFIG_MAP", "SERVER_OVERRIDES", "USER_OVERRIDES", "RESOLVED_PROFILES"}) {
            Map<Object, Object> contents = map(ItemRenderOverrides.class, name);
            previousMaps.put(name, new HashMap<>(contents));
            contents.clear();
        }
    }

    @AfterEach
    void restoreOverrideState() throws ReflectiveOperationException {
        try {
            for (var entry : previousMaps.entrySet()) {
                Map<Object, Object> contents = map(ItemRenderOverrides.class, entry.getKey());
                contents.clear();
                contents.putAll(entry.getValue());
            }
            if (overrideStateInitialized) {
                field(ItemRenderOverrides.class, "userFileLoaded").setBoolean(null, previousUserFileLoaded);
            }
        } finally {
            field(PlatformServices.class, "backend").set(null, previousBackend);
        }
    }

    private static void seedResolvedProfile() throws ReflectiveOperationException {
        // A sentinel key avoids registry initialization; invalidation must release every entry.
        map(ItemRenderOverrides.class, "RESOLVED_PROFILES").put(null,
                new RenderProfile(RenderMode.THREE_D, 2.0f, RenderOffset.ZERO));
    }

    private static void assertProfilesCleared() throws ReflectiveOperationException {
        assertTrue(map(ItemRenderOverrides.class, "RESOLVED_PROFILES").isEmpty());
    }

    @Test
    void serverSyncAndUserEditsInvalidateProfilesAndKeepTheirLayers() throws ReflectiveOperationException {
        seedResolvedProfile();
        ItemRenderOverrides.setSyncedServerOverrides(Map.of(ID, CONFIG));
        assertProfilesCleared();
        assertEquals(CONFIG, map(ItemRenderOverrides.class, "SERVER_OVERRIDES").get(ID));

        seedResolvedProfile();
        ItemRenderOverrides.putUser(ID, CONFIG);
        assertProfilesCleared();
        assertEquals(CONFIG, map(ItemRenderOverrides.class, "USER_OVERRIDES").get(ID));

        seedResolvedProfile();
        ItemRenderOverrides.removeUser(ID);
        assertProfilesCleared();
        assertTrue(map(ItemRenderOverrides.class, "USER_OVERRIDES").isEmpty());
        assertEquals(CONFIG, map(ItemRenderOverrides.class, "SERVER_OVERRIDES").get(ID));

        seedResolvedProfile();
        ItemRenderOverrides.setSyncedServerOverrides(Map.of());
        assertProfilesCleared();
        assertTrue(map(ItemRenderOverrides.class, "SERVER_OVERRIDES").isEmpty());
    }

    @Test
    void resourceOverrideReloadReplacesCorpusAndInvalidatesProfiles() throws ReflectiveOperationException {
        new ItemRenderOverrides().apply(Map.of(ID, CONFIG), null, null);
        seedResolvedProfile();
        new ItemRenderOverrides().apply(Map.of(), null, null);
        assertProfilesCleared();
        assertTrue(map(ItemRenderOverrides.class, "CONFIG_MAP").isEmpty());
    }

    @Test
    void initialAndLaterModelReloadsInvalidateResolvedProfiles() throws ReflectiveOperationException {
        Field initial = field(AutoRenderProfiles.class, "initialResourceLoadSeen");
        boolean previous = initial.getBoolean(null);
        Field loaded = field(AutoRenderProfiles.class, "cacheLoaded");
        Field dirty = field(AutoRenderProfiles.class, "dirty");
        boolean previousLoaded = loaded.getBoolean(null), previousDirty = dirty.getBoolean(null);
        Map<Object, Object> measurements = map(AutoRenderProfiles.class, "CACHE");
        Map<Object, Object> previousMeasurements = new HashMap<>(measurements);
        try {
            initial.setBoolean(null, false);
            seedResolvedProfile();
            AutoRenderProfiles.onResourceReload();
            assertProfilesCleared();
            seedResolvedProfile();
            AutoRenderProfiles.onResourceReload();
            assertProfilesCleared();
        } finally {
            measurements.clear();
            measurements.putAll(previousMeasurements);
            loaded.setBoolean(null, previousLoaded);
            dirty.setBoolean(null, previousDirty);
            initial.setBoolean(null, previous);
        }
    }

    @Test
    void barTextureReloadReleasesCachedAtlasRegions() throws ReflectiveOperationException {
        Map<Object, Object> regions = map(BarStackBER.class, "SPRITE_REGIONS");
        Map<Object, Object> previous = new HashMap<>(regions);
        Map<Object, Object> textures = map(BarTextureStore.class, "textureMap");
        Map<Object, Object> tints = map(BarTextureStore.class, "unmappedTints");
        Map<Object, Object> previousTextures = new HashMap<>(textures);
        Map<Object, Object> previousTints = new HashMap<>(tints);
        try {
            regions.put(null, null);
            new BarTextureStore().apply(Map.of(), null, null);
            assertTrue(regions.isEmpty());
        } finally {
            regions.putAll(previous);
            textures.clear();
            textures.putAll(previousTextures);
            tints.clear();
            tints.putAll(previousTints);
        }
    }
}
