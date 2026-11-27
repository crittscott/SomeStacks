package com.github.crittscott.somestacks.client;

import com.github.crittscott.somestacks.NeoForgeRegistry;
import com.github.crittscott.somestacks.NeoForgeServerConfig;
import com.github.crittscott.somestacks.client.measure.AutoRenderProfiles;
import com.github.crittscott.somestacks.network.ConfigSyncPkt;
import com.mojang.blaze3d.platform.InputConstants;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.neoforge.client.event.AddClientReloadListenersEvent;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.settings.KeyConflictContext;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.GameShuttingDownEvent;

/**
 * Client-side registration: the three block entity renderers, the key binding, the resource reload
 * listeners behind render overrides, bar textures, and measured profiles, and the delivery target
 * for the native synchronized configuration.
 */
public final class NeoForgeClientSetup {
    /** The mod's key-binding category, registered through {@code RegisterKeyMappingsEvent}. */
    private static final KeyMapping.Category CATEGORY =
            new KeyMapping.Category(ClientGestures.KEY_CATEGORY_ID);

    /**
     * The stack modifier, {@code V} by default and rebindable. Held rather than pressed: every
     * gesture asks whether it is down at the moment of the click.
     */
    public static final KeyMapping STACK_MODE_KEY =
            new KeyMapping(
                    ClientGestures.STACK_MODE_KEY_TRANSLATION_KEY,
                    KeyConflictContext.IN_GAME,
                    InputConstants.Type.KEYBOARD,
                    InputConstants.KEY_V,
                    CATEGORY);

    private NeoForgeClientSetup() {}

    /**
     * Runs once from the mod constructor on the physical client: publishes policy from the native
     * config load and reload events, installs the gesture-state sender, subscribes the renderer,
     * key, and reload-listener registrations to the mod bus, and hands join, disconnect, and
     * shutdown to {@link SomeStacksClient}.
     */
    public static void init(IEventBus modBus) {
        modBus.addListener((ModConfigEvent.Loading event) -> applyPolicy(event.getConfig()));
        modBus.addListener((ModConfigEvent.Reloading event) -> applyPolicy(event.getConfig()));
        NeoForgeClientEvents.init();
        modBus.addListener(NeoForgeClientSetup::onRegisterRenderers);
        modBus.addListener(NeoForgeClientSetup::onRegisterKeys);
        modBus.addListener(NeoForgeClientSetup::onRegisterReloadListeners);
        NeoForge.EVENT_BUS.addListener(
                (ClientPlayerNetworkEvent.LoggingIn evt) -> SomeStacksClient.onJoin());
        NeoForge.EVENT_BUS.addListener((ClientPlayerNetworkEvent.LoggingOut evt) -> {
            SomeStacksClient.onDisconnect();
        });
        NeoForge.EVENT_BUS.addListener(
                (GameShuttingDownEvent evt) -> SomeStacksClient.onStopping());
    }

    private static void applyPolicy(ModConfig config) {
        NeoForgeServerConfig backend = NeoForgeServerConfig.INSTANCE;
        if (config.getSpec() != backend.spec) return;
        ConfigSyncPkt snapshot = ConfigSyncPkt.fromSettings(backend.reload());
        Minecraft client = Minecraft.getInstance();
        client.execute(() -> ClientServerSettings.apply(snapshot, client.player));
    }

    private static void onRegisterRenderers(EntityRenderersEvent.RegisterRenderers evt) {
        evt.registerBlockEntityRenderer(
                NeoForgeRegistry.STORAGE_STACK_BE.get(), GridStackBER::storage);
        evt.registerBlockEntityRenderer(
                NeoForgeRegistry.SINGLES_STACK_BE.get(), GridStackBER::singles);
        evt.registerBlockEntityRenderer(NeoForgeRegistry.BAR_STACK_BE.get(), BarStackBER::new);
    }

    private static void onRegisterKeys(RegisterKeyMappingsEvent evt) {
        evt.registerCategory(CATEGORY);
        evt.register(STACK_MODE_KEY);
    }

    private static void onRegisterReloadListeners(AddClientReloadListenersEvent evt) {
        evt.addListener(ItemRenderOverrides.RELOAD_LISTENER_ID, new ItemRenderOverrides());
        evt.addListener(BarTextureStore.RELOAD_LISTENER_ID, new BarTextureStore());
        evt.addListener(
                CubeRenderHelper.RENDER_CACHE_RELOAD_LISTENER_ID,
                (ResourceManagerReloadListener)
                        manager -> {
                            AutoRenderProfiles.onResourceReload();
                            CubeRenderHelper.onResourceReload();
                        });
    }
}
