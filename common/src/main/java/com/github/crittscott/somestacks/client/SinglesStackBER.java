package com.github.crittscott.somestacks.client;

import com.github.crittscott.somestacks.block.SinglesStackBE;
import com.github.crittscott.somestacks.util.SinglesCubeIdx;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;

/**
 * Loader-neutral renderer for a Singles Stack's 64 stored items. Each occupied cell is placed at its
 * corner, rotated with the block's layout, and handed to {@link CubeRenderHelper}, which applies
 * the item's own render profile.
 *
 * <p>Unlike Storage, each item also carries a rotation of its own, applied on top of the block's.
 */
public class SinglesStackBER implements BlockEntityRenderer<SinglesStackBE> {
    private final BlockRenderDispatcher blockRenderer;

    public SinglesStackBER(BlockEntityRendererProvider.Context ctx) {
        this.blockRenderer = ctx.getBlockRenderDispatcher();
    }

    @Override
    public void render(SinglesStackBE be, float partialTick, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        if (be.getLevel() != null) {
            CubeRenderHelper.renderGridItems(
                    be.getItems(), SinglesStackBE.SLOTS, SinglesCubeIdx.GRID, be.getRotation(),
                    CubeRenderHelper.CELL_RENDER_SCALE, be::getCubeRotation,
                    be.getLevel(), be.getBlockPos(), pose, buffers, blockRenderer);
        }
    }

    @Override
    public boolean shouldRenderOffScreen(SinglesStackBE be) {
        return false;
    }
}
