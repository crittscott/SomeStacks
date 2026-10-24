package com.github.crittscott.somestacks.client;

import net.fabricmc.fabric.api.resource.IdentifiableResourceReloadListener;
import net.minecraft.resources.ResourceLocation;

/** Fabric identity for the shared bar-texture reload listener. */
public final class FabricBarTextureStore extends BarTextureStore
        implements IdentifiableResourceReloadListener {
    @Override
    public ResourceLocation getFabricId() {
        return RELOAD_LISTENER_ID;
    }
}
