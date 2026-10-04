package com.github.crittscott.somestacks.forge;

import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.loading.FMLPaths;

import java.nio.file.Path;

/** Forge implementations of the common platform services. */
public final class ForgePlatformServices {
    private ForgePlatformServices() {}

    public static Path configFolder() {
        return FMLPaths.CONFIGDIR.get();
    }

    public static String modVersion(String namespace) {
        return ModList.get().getModContainerById(namespace)
                .map(container -> container.getModInfo().getVersion().toString())
                .orElse("unknown");
    }
}
