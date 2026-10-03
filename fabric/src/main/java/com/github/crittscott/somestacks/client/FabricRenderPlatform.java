package com.github.crittscott.somestacks.client;

import net.fabricmc.loader.api.FabricLoader;

/** Fabric services used by shared client rendering. */
public final class FabricRenderPlatform implements ClientRenderPlatform.Backend {
    @Override
    public String modVersion(String namespace) {
        return FabricLoader.getInstance().getModContainer(namespace)
                .map(container -> container.getMetadata().getVersion().getFriendlyString())
                .orElse("unknown");
    }
}
