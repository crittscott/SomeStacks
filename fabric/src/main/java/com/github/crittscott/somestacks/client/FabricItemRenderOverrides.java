package com.github.crittscott.somestacks.client;

import net.fabricmc.fabric.api.resource.IdentifiableResourceReloadListener;
import net.minecraft.resources.ResourceLocation;

/** Fabric identity for the shared item-render override reload listener. */
public final class FabricItemRenderOverrides extends ItemRenderOverrides
        implements IdentifiableResourceReloadListener {
    @Override
    public ResourceLocation getFabricId() {
        return RELOAD_LISTENER_ID;
    }
}
