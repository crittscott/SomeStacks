package com.github.crittscott.somestacks.client;

import com.github.crittscott.somestacks.SomeStacksCommon;
import net.fabricmc.fabric.api.resource.IdentifiableResourceReloadListener;
import net.minecraft.resources.ResourceLocation;

/** Clears baked-model-dependent render caches after a client resource reload. */
public final class FabricRenderCacheReloadListener
        extends RenderCacheReloadListener implements IdentifiableResourceReloadListener {
    private static final ResourceLocation ID =
            new ResourceLocation(SomeStacksCommon.MODID, "render_caches");

    @Override
    public ResourceLocation getFabricId() {
        return ID;
    }

}
