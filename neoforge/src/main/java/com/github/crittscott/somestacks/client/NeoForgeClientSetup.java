package com.github.crittscott.somestacks.client;

import com.github.crittscott.somestacks.NeoForgeRegistry;
import com.github.crittscott.somestacks.client.measure.AutoRenderProfiles;
import com.github.crittscott.somestacks.NeoForgeServerConfig;
import com.github.crittscott.somestacks.network.ConfigSyncPkt;
import net.minecraft.client.Minecraft;
import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.fml.config.ModConfig;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.AddClientReloadListenersEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.GameShuttingDownEvent;

/**
 * Client-side registration: the three block entity renderers, the key binding, the resource reload
 * listeners behind render overrides, bar textures, and measured profiles, and the delivery target
 * for the native synchronized configuration.
 */
public final class NeoForgeClientSetup {
    private NeoForgeClientSetup() {}

    public static void init(IEventBus modBus) {
        modBus.addListener((ModConfigEvent.Loading event) -> applyPolicy(event.getConfig()));
        modBus.addListener((ModConfigEvent.Reloading event) -> applyPolicy(event.getConfig()));
        NeoForgeClientEvents.init();
        modBus.addListener(NeoForgeClientSetup::onRegisterRenderers);
        modBus.addListener(NeoForgeClientSetup::onRegisterKeys);
        modBus.addListener(NeoForgeClientSetup::onRegisterReloadListeners);
        NeoForge.EVENT_BUS.addListener(
                (ClientPlayerNetworkEvent.LoggingOut evt) -> {
                    ClientGestures.resetSync();
                    ConfigSyncReceiver.clear();
                    AutoRenderProfiles.saveCache();
                });
        NeoForge.EVENT_BUS.addListener(
                (GameShuttingDownEvent evt) -> AutoRenderProfiles.saveCache());
    }

    private static void applyPolicy(ModConfig config) {
        NeoForgeServerConfig backend = NeoForgeServerConfig.INSTANCE;
        if (config.getSpec() != backend.spec) return;
        ConfigSyncPkt snapshot = ConfigSyncPkt.fromSettings(backend.reload());
        Minecraft.getInstance().execute(() -> ConfigSyncReceiver.apply(snapshot));
    }

    private static void onRegisterRenderers(EntityRenderersEvent.RegisterRenderers evt) {
        evt.registerBlockEntityRenderer(NeoForgeRegistry.STORAGE_STACK_BE.get(), StorageStackBER::new);
        evt.registerBlockEntityRenderer(NeoForgeRegistry.SINGLES_STACK_BE.get(), SinglesStackBER::new);
        evt.registerBlockEntityRenderer(NeoForgeRegistry.BAR_STACK_BE.get(), BarStackBER::new);
    }

    private static void onRegisterKeys(RegisterKeyMappingsEvent evt) {
        evt.registerCategory(NeoForgeKeyMappings.CATEGORY);
        evt.register(NeoForgeKeyMappings.STACK_MODE_KEY);
    }

    private static void onRegisterReloadListeners(AddClientReloadListenersEvent evt) {
        evt.addListener(ItemRenderOverrides.RELOAD_LISTENER_ID, new ItemRenderOverrides());
        evt.addListener(BarTextureStore.RELOAD_LISTENER_ID, new BarTextureStore());
        evt.addListener(CubeRenderHelper.RENDER_CACHE_RELOAD_LISTENER_ID,
                (ResourceManagerReloadListener) manager -> {
                    AutoRenderProfiles.onResourceReload();
                    CubeRenderHelper.onResourceReload();
                });
    }
}
