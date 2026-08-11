package com.github.crittscott.somestacks.client;

import net.fabricmc.fabric.api.client.rendering.v1.ColorProviderRegistry;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.color.item.ItemColor;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/** Fabric services used by shared client rendering. */
public final class FabricRenderPlatform implements ClientRenderPlatform.Backend {
    @Override
    public List<BakedModel> renderPasses(
            BakedModel model, ItemStack stack, ItemDisplayContext context) {
        return List.of(model);
    }

    @Override
    public int itemColor(ItemStack stack, int tintIndex) {
        ItemColor provider = ColorProviderRegistry.ITEM.get(stack.getItem());
        return provider == null ? 0xFFFFFFFF : provider.getColor(stack, tintIndex);
    }

    @Override
    public String modVersion(String namespace) {
        return FabricLoader.getInstance().getModContainer(namespace)
                .map(container -> container.getMetadata().getVersion().getFriendlyString())
                .orElse("unknown");
    }
}
