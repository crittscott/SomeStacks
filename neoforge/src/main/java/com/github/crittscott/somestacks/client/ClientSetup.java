package com.github.crittscott.somestacks.client;

import com.github.crittscott.somestacks.ModRegistry;
import com.github.crittscott.somestacks.client.measure.AutoRenderProfiles;
import com.github.crittscott.somestacks.network.ModNetworking;
import com.mojang.blaze3d.platform.InputConstants;

import net.minecraft.client.KeyMapping;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterClientReloadListenersEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.settings.KeyConflictContext;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.GameShuttingDownEvent;

import org.lwjgl.glfw.GLFW;

/**
 * Client-side registration: the three block entity renderers, the key binding, the resource reload
 * listeners behind render overrides, bar textures, and measured profiles, and the delivery target
 * for the server-to-client render configuration payload.
 */
public final class ClientSetup {
    /**
     * The stack modifier, {@code V} by default and rebindable. Held rather than pressed: every
     * gesture asks whether it is down at the moment of the click.
     */
    public static final KeyMapping STACK_MODE_KEY =
            new KeyMapping(
                    ClientGestures.STACK_MODE_KEY_TRANSLATION_KEY,
                    KeyConflictContext.IN_GAME,
                    InputConstants.Type.KEYSYM,
                    GLFW.GLFW_KEY_V,
                    ClientGestures.KEY_CATEGORY_TRANSLATION_KEY);

    private ClientSetup() {}

    /**
     * Runs once from the mod constructor on the physical client: installs the config-snapshot
     * receiver and the gesture-state sender, subscribes the renderer, key, and reload-listener
     * registrations to the mod bus, and on leaving a world clears gesture sync and staged config
     * and saves the measured cache.
     */
    public static void init(IEventBus modBus) {
        ModNetworking.setClientReceiver(ClientRenderPacketSink::apply);
        ClientEvents.init();
        modBus.addListener(ClientSetup::onRegisterRenderers);
        modBus.addListener(ClientSetup::onRegisterKeys);
        modBus.addListener(ClientSetup::onRegisterReloadListeners);
        NeoForge.EVENT_BUS.addListener((ClientPlayerNetworkEvent.LoggingOut evt) -> {
            ClientGestures.resetSync();
            ClientRenderPacketSink.clear();
            AutoRenderProfiles.saveCache();
        });
        NeoForge.EVENT_BUS.addListener(
                (GameShuttingDownEvent evt) -> AutoRenderProfiles.saveCache());
    }

    private static void onRegisterRenderers(EntityRenderersEvent.RegisterRenderers evt) {
        evt.registerBlockEntityRenderer(ModRegistry.STORAGE_STACK_BE.get(), StorageStackBER::new);
        evt.registerBlockEntityRenderer(ModRegistry.SINGLES_STACK_BE.get(), SinglesStackBER::new);
        evt.registerBlockEntityRenderer(ModRegistry.BAR_STACK_BE.get(), BarStackBER::new);
    }

    private static void onRegisterKeys(RegisterKeyMappingsEvent evt) {
        evt.register(STACK_MODE_KEY);
    }

    private static void onRegisterReloadListeners(RegisterClientReloadListenersEvent evt) {
        evt.registerReloadListener(new ItemRenderOverrides());
        evt.registerReloadListener(new BarTextureStore());
        evt.registerReloadListener(
                (ResourceManagerReloadListener)
                        manager -> {
                            AutoRenderProfiles.onResourceReload();
                            CubeRenderHelper.onResourceReload();
                        });
    }
}
