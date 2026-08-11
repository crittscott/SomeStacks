package com.github.crittscott.somestacks.client;

import com.github.crittscott.somestacks.block.StorageStackBE;
import com.github.crittscott.somestacks.util.SlotAccess;
import com.github.crittscott.somestacks.util.StorageCubeIdx;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.BlockPos;
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
        SlotAccess handler = be.getItems();

        if (be.getLevel() == null) return;

        BlockPos bePos = be.getBlockPos();
        int rotation = be.getRotation();
        int cubeLight = LevelRenderer.getLightColor(be.getLevel(), bePos);

        for (int idx = 0; idx < StorageStackBE.SLOTS; idx++) {
            ItemStack stack = handler.getStackInSlot(idx);
            if (stack.isEmpty()) continue;

            int[] xyz = StorageCubeIdx.xyzFromIndex(idx);
            int[] visualXYZ = StorageCubeIdx.rotateXYZ(xyz[0], xyz[1], xyz[2], rotation);

            float sx = StorageCubeIdx.startPixel(visualXYZ[0]) / 16.0f;
            float sy = StorageCubeIdx.startPixel(visualXYZ[1]) / 16.0f;
            float sz = StorageCubeIdx.startPixel(visualXYZ[2]) / 16.0f;

            pose.pushPose();
            pose.translate(sx, sy, sz);
            pose.scale(CubeRenderHelper.CELL_RENDER_SCALE, CubeRenderHelper.CELL_RENDER_SCALE,
                    CubeRenderHelper.CELL_RENDER_SCALE);
            CubeRenderHelper.renderItemInCube(stack, pose, buffers, cubeLight, blockRenderer, be.getLevel());
            pose.popPose();
        }
    }

    @Override
    public boolean shouldRenderOffScreen(StorageStackBE be) {
        return false;
    }
}
