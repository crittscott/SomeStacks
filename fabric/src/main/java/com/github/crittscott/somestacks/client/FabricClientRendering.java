package com.github.crittscott.somestacks.client;

import com.github.crittscott.somestacks.FabricRegistry;
import com.github.crittscott.somestacks.client.measure.AutoRenderProfiles;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderers;
import net.minecraft.server.packs.PackType;

/** Fabric renderer, reload-listener, and measured-cache registration. */
public final class FabricClientRendering {
    private FabricClientRendering() {}

    public static void init() {
        BlockEntityRenderers.register(
                FabricRegistry.STORAGE_STACK_BE, GridStackBER::storage);
        BlockEntityRenderers.register(
                FabricRegistry.SINGLES_STACK_BE, GridStackBER::singles);
        BlockEntityRenderers.register(
                FabricRegistry.BAR_STACK_BE, BarStackBER::new);

        ResourceManagerHelper clientResources = ResourceManagerHelper.get(PackType.CLIENT_RESOURCES);
        clientResources.registerReloadListener(new FabricItemRenderOverrides());
        clientResources.registerReloadListener(new FabricBarTextureStore());
        clientResources.registerReloadListener(new FabricRenderCacheReloadListener());

        ClientLifecycleEvents.CLIENT_STOPPING.register(
                client -> SomeStacksClient.onStopping());
    }
}
