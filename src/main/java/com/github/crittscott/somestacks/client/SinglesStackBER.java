package com.github.crittscott.somestacks.client;

import com.github.crittscott.somestacks.block.SinglesStackBE;
import com.github.crittscott.somestacks.util.SinglesCubeIdx;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.items.IItemHandler;

public class SinglesStackBER implements BlockEntityRenderer<SinglesStackBE> {
    private final BlockRenderDispatcher blockRenderer;

    public SinglesStackBER(BlockEntityRendererProvider.Context ctx) {
        this.blockRenderer = ctx.getBlockRenderDispatcher();
    }

    @Override
    public void render(SinglesStackBE be, float partialTick, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        IItemHandler handler = be.getItems();

        if (be.getLevel() == null) return;

        BlockPos bePos = be.getBlockPos();
        int blockRotation = be.getRotation();
        int cubeLight = LevelRenderer.getLightColor(be.getLevel(), bePos);

        for (int storageIndex = 0; storageIndex < 64; storageIndex++) {
            ItemStack stack = handler.getStackInSlot(storageIndex);
            if (stack.isEmpty()) continue;

            int[] storageXYZ = SinglesCubeIdx.xyzFromIndex(storageIndex);
            int[] visualXYZ = SinglesCubeIdx.rotateXYZ(storageXYZ[0], storageXYZ[1], storageXYZ[2], blockRotation);

            float sx = SinglesCubeIdx.startPixel(visualXYZ[0]) / 16.0f;
            float sy = SinglesCubeIdx.startPixel(visualXYZ[1]) / 16.0f;
            float sz = SinglesCubeIdx.startPixel(visualXYZ[2]) / 16.0f;
            float scale = 8.0f / 16.0f;

            pose.pushPose();
            pose.translate(sx, sy, sz);
            pose.scale(scale, scale, scale);

            int cubeRotation = be.getCubeRotation(storageIndex);
            pose.translate(0.25f, 0.25f, 0.25f);
            pose.mulPose(Axis.YP.rotationDegrees(cubeRotation * 90));
            pose.translate(-0.25f, -0.25f, -0.25f);

            CubeRenderHelper.renderItemInCube(stack, pose, buffers, cubeLight, blockRenderer, be.getLevel());

            pose.popPose();
        }
    }

    @Override
    public boolean shouldRenderOffScreen(SinglesStackBE be) {
        return false;
    }
}
