package com.github.crittscott.somestacks.client;

import com.github.crittscott.somestacks.block.SinglesStackBE;
import com.github.crittscott.somestacks.util.SinglesCubeIdx;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.state.CameraRenderState;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;

/** Extracts Singles layout and per-item rotations and item presentation before submitting the grid. */
public class SinglesStackBER implements BlockEntityRenderer<SinglesStackBE, CubeRenderHelper.GridRenderState> {
    public SinglesStackBER(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public CubeRenderHelper.GridRenderState createRenderState() {
        return new CubeRenderHelper.GridRenderState();
    }

    @Override
    public void extractRenderState(SinglesStackBE be, CubeRenderHelper.GridRenderState state,
                                   float partialTick, Vec3 cameraPos,
                                   @Nullable ModelFeatureRenderer.CrumblingOverlay crumblingOverlay) {
        BlockEntityRenderer.super.extractRenderState(be, state, partialTick, cameraPos, crumblingOverlay);
        CubeRenderHelper.extractGridItems(be.getItems(), SinglesStackBE.SLOTS, SinglesCubeIdx.GRID,
                be.getRotation(), CubeRenderHelper.CELL_RENDER_SCALE, be::getCubeRotation,
                be.getLevel(), be.getBlockPos(), cameraPos, state);
    }

    @Override
    public void submit(CubeRenderHelper.GridRenderState state, PoseStack pose,
                       SubmitNodeCollector collector, CameraRenderState camera) {
        CubeRenderHelper.submitGridItems(state, pose, collector);
    }
}
