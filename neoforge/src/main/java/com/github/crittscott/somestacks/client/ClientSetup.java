package com.github.crittscott.somestacks.client;

import com.github.crittscott.somestacks.ModRegistry;
import com.github.crittscott.somestacks.client.measure.AutoRenderProfiles;
import com.github.crittscott.somestacks.client.measure.ModelMeasurement;
import com.github.crittscott.somestacks.client.measure.ModelMeasurer;
import com.github.crittscott.somestacks.network.ConfigSyncPkt;
import com.github.crittscott.somestacks.network.ModNetworking;
import com.github.crittscott.somestacks.network.RenderOverridePkt;
import com.github.crittscott.somestacks.network.WriteOverridesPkt;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterClientReloadListenersEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.GameShuttingDownEvent;

/**
 * Client-side registration: the three block entity renderers, the key binding, the resource reload
 * listeners behind render overrides, bar textures, and measured profiles, and the delivery target
 * for the render-related server-to-client payloads.
 */
public final class ClientSetup {
    private ClientSetup() {}

    public static void init(IEventBus modBus) {
        ClientRenderPlatform.setBackend(new NeoForgeRenderPlatform());
        ModelMeasurement.setBackend(new ModelMeasurer());
        ModNetworking.setClientReceiver(ClientSetup::deliverRenderPacket);
        ClientEvents.init();
        modBus.addListener(ClientSetup::onRegisterRenderers);
        modBus.addListener(ClientSetup::onRegisterKeys);
        modBus.addListener(ClientSetup::onRegisterReloadListeners);
        NeoForge.EVENT_BUS.addListener(
                (ClientPlayerNetworkEvent.LoggingOut evt) -> AutoRenderProfiles.saveCache());
        NeoForge.EVENT_BUS.addListener(
                (GameShuttingDownEvent evt) -> AutoRenderProfiles.saveCache());
    }

    private static void deliverRenderPacket(CustomPacketPayload payload) {
        if (payload instanceof ConfigSyncPkt packet) {
            ClientRenderPacketSink.apply(packet);
        } else if (payload instanceof RenderOverridePkt packet) {
            ClientRenderPacketSink.apply(packet);
        } else if (payload instanceof WriteOverridesPkt packet) {
            ClientRenderPacketSink.apply(packet);
        }
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
