package com.github.crittscott.somestacks.client;

import com.github.crittscott.somestacks.block.StorageStackBE;
import com.github.crittscott.somestacks.util.StackItemStorage;
import com.github.crittscott.somestacks.util.StorageCubeIdx;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.world.item.ItemStack;

/**
 * Loader-neutral renderer for a Storage Stack's 27 stored stacks. Each occupied slot is placed at its
 * cell's corner, rotated with the block's layout, and handed to {@link CubeRenderHelper}, which
 * applies the item's own render profile.
 *
 * <p>A slot's item count does not affect what is drawn; one stack is one item's worth of art.
 */
public class StorageStackBER implements BlockEntityRenderer<StorageStackBE> {
    private final BlockRenderDispatcher blockRenderer;

    public StorageStackBER(BlockEntityRendererProvider.Context ctx) {
        this.blockRenderer = ctx.getBlockRenderDispatcher();
    }

    @Override
    public void render(StorageStackBE be, float partialTick, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        StackItemStorage handler = be.getItems();

        if (be.getLevel() == null) return;

        int rotation = be.getRotation();

        for (int idx = 0; idx < StorageStackBE.SLOTS; idx++) {
            ItemStack stack = handler.getStackInSlot(idx);
            if (stack.isEmpty()) continue;

            var box = StorageCubeIdx.GRID.localBox(idx, rotation);
            float sx = (float) box.minX;
            float sy = (float) box.minY;
            float sz = (float) box.minZ;

            pose.pushPose();
            pose.translate(sx, sy, sz);
            pose.scale(CubeRenderHelper.STORAGE_CELL_RENDER_SCALE, CubeRenderHelper.STORAGE_CELL_RENDER_SCALE,
                    CubeRenderHelper.STORAGE_CELL_RENDER_SCALE);
            CubeRenderHelper.renderItemInCube(stack, pose, buffers, light, blockRenderer, be.getLevel());
            pose.popPose();
        }
    }
}
