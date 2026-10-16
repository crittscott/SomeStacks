package com.github.crittscott.somestacks.client;

import com.github.crittscott.somestacks.block.StorageStackBE;
import com.github.crittscott.somestacks.util.StorageCubeIdx;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.state.CameraRenderState;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;

/** Extracts Storage layout rotation and item presentation before submitting the grid. */
public class StorageStackBER implements BlockEntityRenderer<StorageStackBE, CubeRenderHelper.GridRenderState> {
    public StorageStackBER(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public CubeRenderHelper.GridRenderState createRenderState() {
        return new CubeRenderHelper.GridRenderState();
    }

    @Override
    public void extractRenderState(StorageStackBE be, CubeRenderHelper.GridRenderState state,
                                   float partialTick, Vec3 cameraPos,
                                   @Nullable ModelFeatureRenderer.CrumblingOverlay crumblingOverlay) {
        BlockEntityRenderer.super.extractRenderState(be, state, partialTick, cameraPos, crumblingOverlay);
        CubeRenderHelper.extractGridItems(be.getItems(), StorageStackBE.SLOTS, StorageCubeIdx.GRID,
                be.getRotation(), CubeRenderHelper.STORAGE_CELL_RENDER_SCALE, index -> 0,
                be.getLevel(), be.getBlockPos(), cameraPos, state);
    }

    @Override
    public void submit(CubeRenderHelper.GridRenderState state, PoseStack pose,
                       SubmitNodeCollector collector, CameraRenderState camera) {
        CubeRenderHelper.submitGridItems(state, pose, collector);
    }
}
