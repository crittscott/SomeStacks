package com.github.crittscott.somestacks.client;

import com.github.crittscott.somestacks.FabricRegistry;
import com.github.crittscott.somestacks.client.measure.AutoRenderProfiles;
import com.mojang.blaze3d.platform.InputConstants;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.resource.v1.ResourceLoader;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderers;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;

import org.lwjgl.glfw.GLFW;

/**
 * Fabric client registration: the three block entity renderers, the key binding, the resource
 * reload listeners behind render overrides, bar textures, and render caches, and measured-cache
 * saving on client stop.
 */
public final class FabricClientRendering {
    private static final KeyMapping.Category CATEGORY =
            KeyMapping.Category.register(ClientGestures.KEY_CATEGORY_ID);

    /**
     * The stack modifier, {@code V} by default and rebindable. Held rather than pressed: every
     * gesture asks whether it is down at the moment of the click.
     */
    public static final KeyMapping STACK_MODE_KEY =
            new KeyMapping(
                    ClientGestures.STACK_MODE_KEY_TRANSLATION_KEY,
                    InputConstants.Type.KEYSYM,
                    GLFW.GLFW_KEY_V,
                    CATEGORY);

    private FabricClientRendering() {}

    /**
     * Registers the renderers, the key binding, the reload listeners, and measured-cache saving on
     * client stop. Runs once from the client entry point.
     */
    public static void init() {
        BlockEntityRenderers.register(FabricRegistry.STORAGE_STACK_BE, GridStackBER::storage);
        BlockEntityRenderers.register(FabricRegistry.SINGLES_STACK_BE, GridStackBER::singles);
        BlockEntityRenderers.register(FabricRegistry.BAR_STACK_BE, BarStackBER::new);

        KeyMappingHelper.registerKeyMapping(STACK_MODE_KEY);

        ResourceLoader clientResources = ResourceLoader.get(PackType.CLIENT_RESOURCES);
        clientResources.registerReloadListener(
                ItemRenderOverrides.RELOAD_LISTENER_ID, new ItemRenderOverrides());
        clientResources.registerReloadListener(
                BarTextureStore.RELOAD_LISTENER_ID, new BarTextureStore());
        clientResources.registerReloadListener(
                CubeRenderHelper.RENDER_CACHE_RELOAD_LISTENER_ID,
                (ResourceManagerReloadListener)
                        manager -> {
                            AutoRenderProfiles.onResourceReload();
                            CubeRenderHelper.onResourceReload();
                        });

        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> SomeStacksClient.onStopping());
    }
}
