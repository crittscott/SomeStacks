package com.github.crittscott.somestacks;

import net.minecraftforge.fml.loading.FMLPaths;

import java.nio.file.Path;

public final class PlatformPathsImpl {
    private PlatformPathsImpl() {}

    public static Path configFolder() {
        return FMLPaths.CONFIGDIR.get();
    }
}
