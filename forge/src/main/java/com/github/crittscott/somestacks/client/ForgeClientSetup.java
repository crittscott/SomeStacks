package com.github.crittscott.somestacks.client;

import com.github.crittscott.somestacks.ForgeRegistry;
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
public final class ForgeClientSetup {
    private ForgeClientSetup() {}

    public static void init(BusGroup modBus) {
        ForgeClientEvents.init();
        EntityRenderersEvent.RegisterRenderers.getBus(modBus).addListener(ForgeClientSetup::onRegisterRenderers);
        RegisterKeyMappingsEvent.getBus(modBus).addListener(ForgeClientSetup::onRegisterKeys);
        RegisterClientReloadListenersEvent.getBus(modBus).addListener(ForgeClientSetup::onRegisterReloadListeners);
        ClientPlayerNetworkEvent.LoggingIn.BUS.addListener(evt -> SomeStacksClient.onJoin());
        ClientPlayerNetworkEvent.LoggingOut.BUS.addListener(evt -> SomeStacksClient.onDisconnect());
        GameShuttingDownEvent.BUS.addListener(evt -> SomeStacksClient.onStopping());
    }

    private static void onRegisterRenderers(EntityRenderersEvent.RegisterRenderers evt) {
        evt.registerBlockEntityRenderer(ForgeRegistry.STORAGE_STACK_BE.get(), GridStackBER::storage);
        evt.registerBlockEntityRenderer(ForgeRegistry.SINGLES_STACK_BE.get(), GridStackBER::singles);
        evt.registerBlockEntityRenderer(ForgeRegistry.BAR_STACK_BE.get(), BarStackBER::new);
    }

    private static void onRegisterKeys(RegisterKeyMappingsEvent evt) {
        evt.register(ForgeKeyMappings.STACK_MODE_KEY);
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
