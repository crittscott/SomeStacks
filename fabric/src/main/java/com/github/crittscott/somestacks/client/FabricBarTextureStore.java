package com.github.crittscott.somestacks.client;

import com.github.crittscott.somestacks.SomeStacksCommon;
import net.fabricmc.fabric.api.resource.IdentifiableResourceReloadListener;
import net.minecraft.resources.ResourceLocation;

/** Fabric identity for the shared bar-texture reload listener. */
public final class FabricBarTextureStore extends BarTextureStore
        implements IdentifiableResourceReloadListener {
    private static final ResourceLocation ID =
            new ResourceLocation(SomeStacksCommon.MODID, "bar_textures");

    @Override
    public ResourceLocation getFabricId() {
        return ID;
    }
}
