package com.github.crittscott.somestacks.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HalfTransparentBlock;
import net.minecraft.world.level.block.StainedGlassPaneBlock;

import java.util.List;

/** Forge services used by shared client rendering. */
public final class ForgeRenderPlatform implements ClientRenderPlatform.Backend {
    @Override
    public List<BakedModel> renderPasses(
            BakedModel model, ItemStack stack, ItemDisplayContext context) {
        return model.getRenderPasses(stack, fabulousFlag(stack, context));
    }

    /**
     * The flag Forge's item renderer uses when selecting a model's passes and render types:
     * false only for the translucent blocks
     * vanilla draws through the indirect buffers outside GUI and first-person contexts.
     */
    public static boolean fabulousFlag(ItemStack stack, ItemDisplayContext context) {
        if (context == ItemDisplayContext.GUI || context.firstPerson()
                || !(stack.getItem() instanceof BlockItem blockItem)) {
            return true;
        }
        Block block = blockItem.getBlock();
        return !(block instanceof HalfTransparentBlock) && !(block instanceof StainedGlassPaneBlock);
    }

    @Override
    public int itemColor(ItemStack stack, int tintIndex) {
        return Minecraft.getInstance().getItemColors().getColor(stack, tintIndex);
    }

}
