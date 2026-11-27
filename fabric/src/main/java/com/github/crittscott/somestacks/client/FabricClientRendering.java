package com.github.crittscott.somestacks.client;

import com.github.crittscott.somestacks.FabricRegistry;
import com.github.crittscott.somestacks.SomeStacksCommon;
import com.github.crittscott.somestacks.client.measure.AutoRenderProfiles;
import com.github.crittscott.somestacks.client.measure.FabricModelMeasurer;
import com.github.crittscott.somestacks.client.measure.ModelMeasurement;
import com.mojang.blaze3d.platform.InputConstants;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.resource.IdentifiableResourceReloadListener;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderers;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackType;

/**
 * Fabric client registration: the three block entity renderers, the key binding, the resource
 * reload listeners behind render overrides, bar textures, and render caches, and measured-cache
 * saving.
 */
public final class FabricClientRendering {
    /**
     * The stack modifier, {@code V} by default and rebindable. Held rather than pressed: every
     * gesture asks whether it is down at the moment of the click.
     */
    public static final KeyMapping STACK_MODE_KEY =
            new KeyMapping(
                    ClientGestures.STACK_MODIFIER_KEY_ID,
                    InputConstants.Type.KEYSYM,
                    ClientGestures.DEFAULT_STACK_MODIFIER_KEY,
                    ClientGestures.KEY_CATEGORY);

    private FabricClientRendering() {}

    /** Fabric identity for the shared bar-texture reload listener. */
    private static final class BarTextureListener extends BarTextureStore
            implements IdentifiableResourceReloadListener {
        @Override
        public ResourceLocation getFabricId() {
            return new ResourceLocation(SomeStacksCommon.MODID, "bar_textures");
        }
    }

    /** Fabric identity for the shared item-render override reload listener. */
    private static final class ItemOverridesListener extends ItemRenderOverrides
            implements IdentifiableResourceReloadListener {
        @Override
        public ResourceLocation getFabricId() {
            return new ResourceLocation(SomeStacksCommon.MODID, "item_render_overrides");
        }
    }

    /** Fabric identity for the listener that clears baked-model-dependent render caches. */
    private static final class RenderCacheListener extends RenderCacheReloadListener
            implements IdentifiableResourceReloadListener {
        @Override
        public ResourceLocation getFabricId() {
            return new ResourceLocation(SomeStacksCommon.MODID, "render_caches");
        }
    }

    /**
     * Installs the client render and measurement backends and registers the renderers, the key
     * binding, the reload listeners, and measured-cache saving on disconnect and client stop. Runs
     * once from the client entry point.
     */
    public static void init() {
        ClientRenderPlatform.setBackend(new FabricRenderPlatform());
        ModelMeasurement.setBackend(new FabricModelMeasurer());

        BlockEntityRenderers.register(FabricRegistry.STORAGE_STACK_BE, StorageStackBER::new);
        BlockEntityRenderers.register(FabricRegistry.SINGLES_STACK_BE, SinglesStackBER::new);
        BlockEntityRenderers.register(FabricRegistry.BAR_STACK_BE, BarStackBER::new);

        KeyBindingHelper.registerKeyBinding(STACK_MODE_KEY);

        ResourceManagerHelper clientResources =
                ResourceManagerHelper.get(PackType.CLIENT_RESOURCES);
        clientResources.registerReloadListener(new ItemOverridesListener());
        clientResources.registerReloadListener(new BarTextureListener());
        clientResources.registerReloadListener(new RenderCacheListener());

        ClientPlayConnectionEvents.DISCONNECT.register(
                (handler, client) -> AutoRenderProfiles.saveCache());
        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> AutoRenderProfiles.saveCache());
    }
}
