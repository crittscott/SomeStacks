package com.github.crittscott.somestacks.client;

import com.github.crittscott.somestacks.ModRegistry;
import com.github.crittscott.somestacks.client.measure.AutoRenderProfiles;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.client.event.RegisterClientReloadListenersEvent;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.event.GameShuttingDownEvent;
import net.minecraftforge.eventbus.api.bus.BusGroup;

/**
 * Client-side registration: the three block entity renderers, the key binding, and the resource
 * reload listeners behind render overrides, bar textures, and measured profiles.
 *
 * <p>Also the measured cache's two save points. It is written when the player leaves a world and
 * when the game shuts down, rather than on every measurement.
 */
public final class ClientSetup {
    private ClientSetup() {}

    public static void init(BusGroup modBus) {
        ClientEvents.init();
        EntityRenderersEvent.RegisterRenderers.getBus(modBus).addListener(ClientSetup::onRegisterRenderers);
        RegisterKeyMappingsEvent.getBus(modBus).addListener(ClientSetup::onRegisterKeys);
        RegisterClientReloadListenersEvent.getBus(modBus).addListener(ClientSetup::onRegisterReloadListeners);
        ClientPlayerNetworkEvent.LoggingOut.BUS.addListener(evt -> {
            ClientGestures.resetSync();
            ClientRenderPacketSink.clear();
            AutoRenderProfiles.saveCache();
        });
        GameShuttingDownEvent.BUS.addListener(evt -> AutoRenderProfiles.saveCache());
    }

    private static void onRegisterRenderers(EntityRenderersEvent.RegisterRenderers evt) {
        evt.registerBlockEntityRenderer(ModRegistry.STORAGE_STACK_BE.get(), StorageStackBER::new);
        evt.registerBlockEntityRenderer(ModRegistry.SINGLES_STACK_BE.get(), SinglesStackBER::new);
        evt.registerBlockEntityRenderer(ModRegistry.BAR_STACK_BE.get(), BarStackBER::new);
    }

    private static void onRegisterKeys(RegisterKeyMappingsEvent evt) {
        evt.register(KeyMappings.STACK_MODE_KEY);
    }

    private static void onRegisterReloadListeners(RegisterClientReloadListenersEvent evt) {
        evt.registerReloadListener(new ItemRenderOverrides());
        evt.registerReloadListener(new BarTextureStore());
        evt.registerReloadListener((ResourceManagerReloadListener) manager -> {
            AutoRenderProfiles.onResourceReload();
            CubeRenderHelper.onResourceReload();
        });
    }
}
