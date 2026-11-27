package com.github.crittscott.somestacks.client;

import com.github.crittscott.somestacks.ForgeRegistry;
import com.github.crittscott.somestacks.client.measure.AutoRenderProfiles;
import com.mojang.blaze3d.platform.InputConstants;

import net.minecraft.client.KeyMapping;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.client.event.RegisterClientReloadListenersEvent;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.settings.KeyConflictContext;
import net.minecraftforge.event.GameShuttingDownEvent;
import net.minecraftforge.eventbus.api.bus.BusGroup;

import org.lwjgl.glfw.GLFW;

/**
 * Client-side registration: the three block entity renderers, the key binding, and the resource
 * reload listeners behind render overrides, bar textures, and measured profiles.
 *
 * <p>Also the measured cache's two save points. It is written when the player leaves a world and
 * when the game shuts down, rather than on every measurement.
 */
public final class ForgeClientSetup {
    /** The mod's key-binding category, registered with vanilla when this class loads. */
    private static final KeyMapping.Category CATEGORY =
            KeyMapping.Category.register(ClientGestures.KEY_CATEGORY_ID);

    /** Position within the category; the category holds only the stack modifier. */
    private static final int STACK_MODE_KEY_SORT_ORDER = 0;

    /**
     * The stack modifier, {@code V} by default and rebindable. Held rather than pressed: every
     * gesture asks whether it is down at the moment of the click.
     */
    public static final KeyMapping STACK_MODE_KEY =
            new KeyMapping(
                    ClientGestures.STACK_MODE_KEY_TRANSLATION_KEY,
                    KeyConflictContext.IN_GAME,
                    InputConstants.Type.KEYSYM.getOrCreate(GLFW.GLFW_KEY_V),
                    CATEGORY,
                    STACK_MODE_KEY_SORT_ORDER);

    private ForgeClientSetup() {}

    /**
     * Runs once from the mod constructor on the physical client: installs the gesture-state
     * sender, subscribes the renderer, key, and reload-listener registrations to the mod bus, and
     * hands join, disconnect, and shutdown to {@link SomeStacksClient}.
     */
    public static void init(BusGroup modBus) {
        ForgeClientEvents.init();
        EntityRenderersEvent.RegisterRenderers.getBus(modBus)
                .addListener(ForgeClientSetup::onRegisterRenderers);
        RegisterKeyMappingsEvent.getBus(modBus).addListener(ForgeClientSetup::onRegisterKeys);
        RegisterClientReloadListenersEvent.getBus(modBus)
                .addListener(ForgeClientSetup::onRegisterReloadListeners);
        ClientPlayerNetworkEvent.LoggingIn.BUS.addListener(evt -> SomeStacksClient.onJoin());
        ClientPlayerNetworkEvent.LoggingOut.BUS.addListener(evt -> SomeStacksClient.onDisconnect());
        GameShuttingDownEvent.BUS.addListener(evt -> SomeStacksClient.onStopping());
    }

    private static void onRegisterRenderers(EntityRenderersEvent.RegisterRenderers evt) {
        evt.registerBlockEntityRenderer(
                ForgeRegistry.STORAGE_STACK_BE.get(), GridStackBER::storage);
        evt.registerBlockEntityRenderer(
                ForgeRegistry.SINGLES_STACK_BE.get(), GridStackBER::singles);
        evt.registerBlockEntityRenderer(ForgeRegistry.BAR_STACK_BE.get(), BarStackBER::new);
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
