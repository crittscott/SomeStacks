package com.github.crittscott.somestacks.client;

import com.github.crittscott.somestacks.ModRegistry;
import com.github.crittscott.somestacks.client.measure.AutoRenderProfiles;
import com.github.crittscott.somestacks.client.measure.ModelMeasurement;
import com.github.crittscott.somestacks.client.measure.ModelMeasurer;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.client.event.RegisterClientReloadListenersEvent;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.GameShuttingDownEvent;
import net.minecraftforge.eventbus.api.IEventBus;

/**
 * Client-side registration: the three block entity renderers, the key binding, and the resource
 * reload listeners behind render overrides, bar textures, and measured profiles.
 *
 * <p>Also the measured cache's two save points. It is written when the player leaves a world and
 * when the game shuts down, rather than on every measurement.
 */
public final class ClientSetup {
    private ClientSetup() {}

    public static void init(IEventBus modBus) {
        ClientRenderPlatform.setBackend(new ForgeRenderPlatform());
        ModelMeasurement.setBackend(new ModelMeasurer());
        ClientEvents.init();
        modBus.addListener(ClientSetup::onRegisterRenderers);
        modBus.addListener(ClientSetup::onRegisterKeys);
        modBus.addListener(ClientSetup::onRegisterReloadListeners);
        MinecraftForge.EVENT_BUS.addListener((ClientPlayerNetworkEvent.LoggingOut evt) -> AutoRenderProfiles.saveCache());
        MinecraftForge.EVENT_BUS.addListener((GameShuttingDownEvent evt) -> AutoRenderProfiles.saveCache());
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
