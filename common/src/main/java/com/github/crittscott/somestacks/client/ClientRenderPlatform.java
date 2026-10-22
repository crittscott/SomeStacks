package com.github.crittscott.somestacks.client;

import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/** Loader-specific services used by shared client rendering code. */
public final class ClientRenderPlatform {
    /** Fixed quad-selection seed used by ItemRenderer for inventory model variants. */
    public static final long ITEM_MODEL_SEED = 42L;

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

}
