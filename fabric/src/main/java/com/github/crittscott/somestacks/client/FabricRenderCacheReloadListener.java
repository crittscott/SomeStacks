package com.github.crittscott.somestacks.client;

import com.github.crittscott.somestacks.SomeStacksCommon;
import com.github.crittscott.somestacks.client.measure.AutoRenderProfiles;
import net.fabricmc.fabric.api.resource.IdentifiableResourceReloadListener;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;

/** Clears baked-model-dependent render caches after a client resource reload. */
public final class FabricRenderCacheReloadListener
        implements IdentifiableResourceReloadListener, ResourceManagerReloadListener {
    private static final ResourceLocation ID =
            new ResourceLocation(SomeStacksCommon.MODID, "render_caches");

    @Override
    public ResourceLocation getFabricId() {
        return ID;
    }

    @Override
    public void onResourceManagerReload(ResourceManager resourceManager) {
        AutoRenderProfiles.onResourceReload();
        CubeRenderHelper.onResourceReload();
    }
}
