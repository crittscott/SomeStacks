package com.github.crittscott.somestacks.client;

import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/** Loader-specific services used by shared client rendering code. */
public final class ClientRenderPlatform {
    private ClientRenderPlatform() {}

    public interface Backend {
        /**
         * The sequence of baked models to render for {@code stack} in {@code context}, resolved
         * through the loader's own item renderer so multi-layer items (e.g. a fabulous-graphics
         * double pass) render exactly as they would through vanilla's item rendering path.
         */
        List<BakedModel> renderPasses(
                BakedModel model, ItemStack stack, ItemDisplayContext context);

        /**
         * The tint for layer {@code tintIndex} of {@code stack}, resolved through the loader's
         * item color registry the way a vanilla tinted item model would be colored.
         */
        int itemColor(ItemStack stack, int tintIndex);

        /**
         * The installed version string of the mod with the given {@code namespace}, or a
         * placeholder if it isn't loaded. Used to detect when a measured item's source mod has
         * updated and cached measurements need to be invalidated.
         */
        String modVersion(String namespace);
    }

    private static Backend backend;

    /** Installs the loader-specific implementation; called once during client setup. */
    public static void setBackend(Backend backend) {
        ClientRenderPlatform.backend = backend;
    }

    /** @see Backend#renderPasses */
    public static List<BakedModel> renderPasses(
            BakedModel model, ItemStack stack, ItemDisplayContext context) {
        return backend.renderPasses(model, stack, context);
    }

    /** @see Backend#itemColor */
    public static int itemColor(ItemStack stack, int tintIndex) {
        return backend.itemColor(stack, tintIndex);
    }

    /** @see Backend#modVersion */
    public static String modVersion(String namespace) {
        return backend.modVersion(namespace);
    }
}
