package com.github.crittscott.somestacks.client;

import com.github.crittscott.somestacks.client.measure.AutoRenderProfiles;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;

/** Clears model-dependent client caches after resource reload. */
public class RenderCacheReloadListener implements ResourceManagerReloadListener {
    @Override
    public void onResourceManagerReload(ResourceManager resourceManager) {
        AutoRenderProfiles.onResourceReload();
        CubeRenderHelper.onResourceReload();
    }
}
