package com.github.crittscott.somestacks.client;

import com.github.crittscott.somestacks.block.StorageStackBE;
import com.github.crittscott.somestacks.util.StorageCubeIdx;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.items.IItemHandler;

public class StorageStackBER implements BlockEntityRenderer<StorageStackBE> {
    private final BlockRenderDispatcher blockRenderer;

    /**
     * StorageCubeIdx includes a 1-pixel margin in its start positions for visual gaps
     * and collision detection. However, CubeRenderHelper's centering logic assumes
     * the cube coordinate system starts at origin. We subtract this margin to align
     * the rendering coordinate system properly.
     */
    //private static final float RENDER_MARGIN_OFFSET = 1.0f / 16.0f;
    private static final float RENDER_MARGIN_OFFSET = 0.0f / 16.0f;
    // TODO: this is a band aid, and an unnecessary one.

    public StorageStackBER(BlockEntityRendererProvider.Context ctx) {
        this.blockRenderer = ctx.getBlockRenderDispatcher();
    }

    @Override
    public void render(StorageStackBE be, float partialTick, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        IItemHandler handler = be.getCapability(ForgeCapabilities.ITEM_HANDLER).orElse(null);
        if (handler == null) return;

        if (be.getLevel() == null) return;

        BlockPos bePos = be.getBlockPos();
        int rotation = be.getRotation();
        int cubeLight = LevelRenderer.getLightColor(be.getLevel(), bePos);

        for (int idx = 0; idx < 27; idx++) {
            ItemStack stack = handler.getStackInSlot(idx);
            if (stack.isEmpty()) continue;

            int[] xyz = StorageCubeIdx.xyzFromIndex(idx);
            int[] visualXYZ = StorageCubeIdx.rotateXYZ(xyz[0], xyz[1], xyz[2], rotation);

            float sx = StorageCubeIdx.startPixel(visualXYZ[0]) / 16.0f;
            float sy = StorageCubeIdx.startPixel(visualXYZ[1]) / 16.0f;
            float sz = StorageCubeIdx.startPixel(visualXYZ[2]) / 16.0f;

            pose.pushPose();
            pose.translate(sx - RENDER_MARGIN_OFFSET, sy - RENDER_MARGIN_OFFSET, sz - RENDER_MARGIN_OFFSET);
            pose.scale(0.5f, 0.5f, 0.5f);
            CubeRenderHelper.renderItemInCube(stack, pose, buffers, cubeLight, blockRenderer, be.getLevel());
            pose.popPose();
        }
    }

    @Override
    public boolean shouldRenderOffScreen(StorageStackBE be) {
        return false;
    }
}
