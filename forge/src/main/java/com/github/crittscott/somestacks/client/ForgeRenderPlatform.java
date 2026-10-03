package com.github.crittscott.somestacks.client;

import net.minecraftforge.fml.ModList;

/** Forge services used by shared client rendering. */
public final class ForgeRenderPlatform implements ClientRenderPlatform.Backend {
    @Override
    public String modVersion(String namespace) {
        return ModList.get().getModContainerById(namespace)
                .map(container -> container.getModInfo().getVersion().toString())
                .orElse("unknown");
    }
}
