package com.github.crittscott.somestacks.client;

import com.github.crittscott.somestacks.FabricRegistry;
import com.github.crittscott.somestacks.client.measure.AutoRenderProfiles;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.resource.v1.ResourceLoader;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderers;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;

/** Fabric renderer, reload-listener, and measured-cache registration. */
public final class FabricClientRendering {
    private FabricClientRendering() {}

    public static void init() {
        BlockEntityRenderers.register(
                FabricRegistry.STORAGE_STACK_BE, StorageStackBER::new);
        BlockEntityRenderers.register(
                FabricRegistry.SINGLES_STACK_BE, SinglesStackBER::new);
        BlockEntityRenderers.register(
                FabricRegistry.BAR_STACK_BE, BarStackBER::new);

        ResourceLoader clientResources = ResourceLoader.get(PackType.CLIENT_RESOURCES);
        clientResources.registerReloader(ItemRenderOverrides.RELOAD_LISTENER_ID, new ItemRenderOverrides());
        clientResources.registerReloader(BarTextureStore.RELOAD_LISTENER_ID, new BarTextureStore());
        clientResources.registerReloader(CubeRenderHelper.RENDER_CACHE_RELOAD_LISTENER_ID,
                (ResourceManagerReloadListener) manager -> {
                    AutoRenderProfiles.onResourceReload();
                    CubeRenderHelper.onResourceReload();
                });

        ClientPlayConnectionEvents.DISCONNECT.register(
                (handler, client) -> AutoRenderProfiles.saveCache());
        ClientLifecycleEvents.CLIENT_STOPPING.register(
                client -> AutoRenderProfiles.saveCache());
    }
}
