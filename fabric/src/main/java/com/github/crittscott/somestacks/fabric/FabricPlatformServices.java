package com.github.crittscott.somestacks.fabric;

import net.fabricmc.loader.api.FabricLoader;

import java.nio.file.Path;

/** Fabric implementations of the common platform services. */
public final class FabricPlatformServices {
    private FabricPlatformServices() {}

    public static Path configFolder() {
        return FabricLoader.getInstance().getConfigDir();
    }

    public static String modVersion(String namespace) {
        return FabricLoader.getInstance().getModContainer(namespace)
                .map(container -> container.getMetadata().getVersion().getFriendlyString())
                .orElse("unknown");
    }
}
