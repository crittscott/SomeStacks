package com.github.crittscott.somestacks.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fml.ModList;

import java.util.List;

/** Forge services used by shared client rendering. */
public final class ForgeRenderPlatform implements ClientRenderPlatform.Backend {
    @Override
    public List<BakedModel> renderPasses(
            BakedModel model, ItemStack stack, ItemDisplayContext context) {
        return model.getRenderPasses(stack, CubeRenderHelper.fabulousFlag(stack, context));
    }

    @Override
    public int itemColor(ItemStack stack, int tintIndex) {
        return Minecraft.getInstance().getItemColors().getColor(stack, tintIndex);
    }

    @Override
    public String modVersion(String namespace) {
        return ModList.get().getModContainerById(namespace)
                .map(container -> container.getModInfo().getVersion().toString())
                .orElse("unknown");
    }
}
