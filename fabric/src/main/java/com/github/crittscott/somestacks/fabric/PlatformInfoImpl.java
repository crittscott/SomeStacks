package com.github.crittscott.somestacks.fabric;

import net.fabricmc.loader.api.FabricLoader;

public final class PlatformInfoImpl {
    private PlatformInfoImpl() {}

    public static String modVersion(String namespace) {
        return FabricLoader.getInstance().getModContainer(namespace)
                .map(container -> container.getMetadata().getVersion().getFriendlyString())
                .orElse("unknown");
    }
}
