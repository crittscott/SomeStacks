package com.github.crittscott.somestacks.client;

import com.github.crittscott.somestacks.FabricRegistry;
import com.github.crittscott.somestacks.client.measure.AutoRenderProfiles;
import com.mojang.blaze3d.platform.InputConstants;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.resource.IdentifiableResourceReloadListener;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderers;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;

import org.lwjgl.glfw.GLFW;

/**
 * Fabric client registration: the three block entity renderers, the key binding, the resource
 * reload listeners behind render overrides, bar textures, and render caches, and measured-cache
 * saving on client stop.
 */
public final class FabricClientRendering {
    /**
     * The stack modifier, {@code V} by default and rebindable. Held rather than pressed: every
     * gesture asks whether it is down at the moment of the click.
     */
    public static final KeyMapping STACK_MODE_KEY =
            new KeyMapping(
                    ClientGestures.STACK_MODE_KEY_TRANSLATION_KEY,
                    InputConstants.Type.KEYSYM,
                    GLFW.GLFW_KEY_V,
                    ClientGestures.KEY_CATEGORY_TRANSLATION_KEY);

    private FabricClientRendering() {}

    /** Fabric identity for the shared bar-texture reload listener. */
    private static final class BarTextureListener extends BarTextureStore
            implements IdentifiableResourceReloadListener {
        @Override
        public ResourceLocation getFabricId() {
            return RELOAD_LISTENER_ID;
        }
    }

    /** Fabric identity for the shared item-render override reload listener. */
    private static final class ItemOverridesListener extends ItemRenderOverrides
            implements IdentifiableResourceReloadListener {
        @Override
        public ResourceLocation getFabricId() {
            return RELOAD_LISTENER_ID;
        }
    }

    /** Clears baked-model-dependent render caches after a client resource reload. */
    private static final class RenderCacheListener
            implements IdentifiableResourceReloadListener, ResourceManagerReloadListener {
        @Override
        public ResourceLocation getFabricId() {
            return CubeRenderHelper.RENDER_CACHE_RELOAD_LISTENER_ID;
        }

        @Override
        public void onResourceManagerReload(ResourceManager resourceManager) {
            AutoRenderProfiles.onResourceReload();
            CubeRenderHelper.onResourceReload();
        }
    }

    /**
     * Registers the renderers, the key binding, the reload listeners, and measured-cache saving on
     * client stop. Runs once from the client entry point.
     */
    public static void init() {
        BlockEntityRenderers.register(FabricRegistry.STORAGE_STACK_BE, GridStackBER::storage);
        BlockEntityRenderers.register(FabricRegistry.SINGLES_STACK_BE, GridStackBER::singles);
        BlockEntityRenderers.register(FabricRegistry.BAR_STACK_BE, BarStackBER::new);

        KeyBindingHelper.registerKeyBinding(STACK_MODE_KEY);

        ResourceManagerHelper clientResources =
                ResourceManagerHelper.get(PackType.CLIENT_RESOURCES);
        clientResources.registerReloadListener(new ItemOverridesListener());
        clientResources.registerReloadListener(new BarTextureListener());
        clientResources.registerReloadListener(new RenderCacheListener());

        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> SomeStacksClient.onStopping());
    }
}
