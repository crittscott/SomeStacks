package com.github.crittscott.somestacks.neoforge;

import net.neoforged.fml.ModList;
import net.neoforged.fml.loading.FMLPaths;

import java.nio.file.Path;

/** NeoForge implementations of the common platform services. */
public final class NeoForgePlatformServices {
    private NeoForgePlatformServices() {}

    public static Path configFolder() {
        return FMLPaths.CONFIGDIR.get();
    }

    public static String modVersion(String namespace) {
        return ModList.get().getModContainerById(namespace)
                .map(container -> container.getModInfo().getVersion().toString())
                .orElse("unknown");
    }
}
