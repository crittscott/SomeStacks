package com.github.crittscott.somestacks.client;

import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/** Loader-specific services used by shared client rendering code. */
public final class ClientRenderPlatform {
    private ClientRenderPlatform() {}

    public interface Backend {
        List<BakedModel> renderPasses(
                BakedModel model, ItemStack stack, ItemDisplayContext context);

        int itemColor(ItemStack stack, int tintIndex);

        String modVersion(String namespace);
    }

    private static Backend backend;

    public static void setBackend(Backend backend) {
        ClientRenderPlatform.backend = backend;
    }

    public static List<BakedModel> renderPasses(
            BakedModel model, ItemStack stack, ItemDisplayContext context) {
        return backend.renderPasses(model, stack, context);
    }

    public static int itemColor(ItemStack stack, int tintIndex) {
        return backend.itemColor(stack, tintIndex);
    }

    public static String modVersion(String namespace) {
        return backend.modVersion(namespace);
    }
}
