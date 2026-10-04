package com.github.crittscott.somestacks.forge;

import net.minecraftforge.fml.ModList;

public final class PlatformInfoImpl {
    private PlatformInfoImpl() {}

    public static String modVersion(String namespace) {
        return ModList.get().getModContainerById(namespace)
                .map(container -> container.getModInfo().getVersion().toString())
                .orElse("unknown");
    }
}
