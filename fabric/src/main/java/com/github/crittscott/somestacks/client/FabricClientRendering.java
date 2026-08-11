package com.github.crittscott.somestacks.client;

import com.github.crittscott.somestacks.FabricRegistry;
import com.github.crittscott.somestacks.client.measure.AutoRenderProfiles;
import com.github.crittscott.somestacks.client.measure.FabricModelMeasurer;
import com.github.crittscott.somestacks.client.measure.ModelMeasurement;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.BlockEntityRendererRegistry;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.minecraft.server.packs.PackType;

/** Fabric renderer, reload-listener, and measured-cache registration. */
public final class FabricClientRendering {
    private FabricClientRendering() {}

    public static void init() {
        ClientRenderPlatform.setBackend(new FabricRenderPlatform());
        ModelMeasurement.setBackend(new FabricModelMeasurer());
        ClientRenderPacketSink.setHandler(new DefaultClientRenderPacketHandler());

        BlockEntityRendererRegistry.register(
                FabricRegistry.STORAGE_STACK_BE, StorageStackBER::new);
        BlockEntityRendererRegistry.register(
                FabricRegistry.SINGLES_STACK_BE, SinglesStackBER::new);
        BlockEntityRendererRegistry.register(
                FabricRegistry.BAR_STACK_BE, BarStackBER::new);

        ResourceManagerHelper clientResources = ResourceManagerHelper.get(PackType.CLIENT_RESOURCES);
        clientResources.registerReloadListener(new FabricItemRenderOverrides());
        clientResources.registerReloadListener(new FabricBarTextureStore());
        clientResources.registerReloadListener(new FabricRenderCacheReloadListener());

        ClientPlayConnectionEvents.DISCONNECT.register(
                (handler, client) -> AutoRenderProfiles.saveCache());
        ClientLifecycleEvents.CLIENT_STOPPING.register(
                client -> AutoRenderProfiles.saveCache());
    }
}
