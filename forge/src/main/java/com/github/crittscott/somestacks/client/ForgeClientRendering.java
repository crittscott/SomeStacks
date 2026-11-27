package com.github.crittscott.somestacks.client;

import com.github.crittscott.somestacks.ForgeRegistry;
import com.github.crittscott.somestacks.client.measure.ForgeModelMeasurer;
import com.github.crittscott.somestacks.client.measure.ModelMeasurement;
import com.mojang.blaze3d.platform.InputConstants;

import net.minecraft.client.KeyMapping;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.client.event.RegisterClientReloadListenersEvent;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.settings.KeyConflictContext;
import net.minecraftforge.eventbus.api.IEventBus;

/**
 * Client-side registration: the three block entity renderers, the key binding, and the resource
 * reload listeners behind render overrides, bar textures, and measured profiles.
 */
public final class ForgeClientRendering {
    /**
     * The stack modifier, {@code V} by default and rebindable. Held rather than pressed: every
     * gesture asks whether it is down at the moment of the click.
     */
    public static final KeyMapping STACK_MODE_KEY =
            new KeyMapping(
                    ClientGestures.STACK_MODIFIER_KEY_ID,
                    KeyConflictContext.IN_GAME,
                    InputConstants.Type.KEYSYM,
                    ClientGestures.DEFAULT_STACK_MODIFIER_KEY,
                    ClientGestures.KEY_CATEGORY);

    private ForgeClientRendering() {}

    /**
     * Installs the client render and measurement backends and the gesture packet sender, then
     * subscribes the client registration listeners to the mod bus. Runs once, on the physical
     * client only, from the mod constructor.
     */
    public static void init(IEventBus modBus) {
        ClientRenderPlatform.setBackend(new ForgeRenderPlatform());
        ModelMeasurement.setBackend(new ForgeModelMeasurer());
        ForgeClientEvents.init();
        modBus.addListener(ForgeClientRendering::onRegisterRenderers);
        modBus.addListener(ForgeClientRendering::onRegisterKeys);
        modBus.addListener(ForgeClientRendering::onRegisterReloadListeners);
    }

    private static void onRegisterKeys(RegisterKeyMappingsEvent evt) {
        evt.register(STACK_MODE_KEY);
    }

    private static void onRegisterReloadListeners(RegisterClientReloadListenersEvent evt) {
        evt.registerReloadListener(new ItemRenderOverrides());
        evt.registerReloadListener(new BarTextureStore());
        evt.registerReloadListener(new RenderCacheReloadListener());
    }

    private static void onRegisterRenderers(EntityRenderersEvent.RegisterRenderers evt) {
        evt.registerBlockEntityRenderer(ForgeRegistry.STORAGE_STACK_BE.get(), StorageStackBER::new);
        evt.registerBlockEntityRenderer(ForgeRegistry.SINGLES_STACK_BE.get(), SinglesStackBER::new);
        evt.registerBlockEntityRenderer(ForgeRegistry.BAR_STACK_BE.get(), BarStackBER::new);
    }
}
