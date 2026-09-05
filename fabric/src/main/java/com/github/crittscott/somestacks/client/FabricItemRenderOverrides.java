package com.github.crittscott.somestacks.client;

import com.github.crittscott.somestacks.SomeStacksCommon;
import net.fabricmc.fabric.api.resource.IdentifiableResourceReloadListener;
import net.minecraft.resources.ResourceLocation;

/** Fabric identity for the shared item-render override reload listener. */
public final class FabricItemRenderOverrides extends ItemRenderOverrides
        implements IdentifiableResourceReloadListener {
    private static final ResourceLocation ID =
            ResourceLocation.fromNamespaceAndPath(SomeStacksCommon.MODID, "item_render_overrides");

    @Override
    public ResourceLocation getFabricId() {
        return ID;
    }
}
