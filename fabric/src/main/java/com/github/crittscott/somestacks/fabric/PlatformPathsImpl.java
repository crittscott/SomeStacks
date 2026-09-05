package com.github.crittscott.somestacks.fabric;

import net.fabricmc.loader.api.FabricLoader;

import java.nio.file.Path;

public final class PlatformPathsImpl {
    private PlatformPathsImpl() {}

    public static Path configFolder() {
        return FabricLoader.getInstance().getConfigDir();
    }
}
