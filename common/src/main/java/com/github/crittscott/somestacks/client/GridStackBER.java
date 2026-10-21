package com.github.crittscott.somestacks.client;

import com.github.crittscott.somestacks.block.StorageStackBE;
import com.github.crittscott.somestacks.block.SinglesStackBE;
import com.github.crittscott.somestacks.block.StackBlockEntity;
import com.github.crittscott.somestacks.util.CubeGrid;
import com.github.crittscott.somestacks.util.SinglesCubeIdx;
import java.util.function.ToIntFunction;
import java.util.function.Function;
import java.util.function.IntUnaryOperator;
import com.github.crittscott.somestacks.util.StorageCubeIdx;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.state.CameraRenderState;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;

/** Extracts grid presentation and submits the same cached geometry for Storage and Singles. */
public final class GridStackBER<T extends StackBlockEntity> implements BlockEntityRenderer<T, CubeRenderHelper.GridRenderState> {
    private final CubeGrid grid;
    private final float scale;
    private final ToIntFunction<T> rotation;
    private final Function<T, IntUnaryOperator> itemRotation;

    private GridStackBER(BlockEntityRendererProvider.Context context, CubeGrid grid, float scale,
                         ToIntFunction<T> rotation, Function<T, IntUnaryOperator> itemRotation) {
        this.grid = grid;
        this.scale = scale;
        this.rotation = rotation;
        this.itemRotation = itemRotation;
    }

    public static GridStackBER<StorageStackBE> storage(BlockEntityRendererProvider.Context context) {
        return new GridStackBER<>(context, StorageCubeIdx.GRID,
                CubeRenderHelper.STORAGE_CELL_RENDER_SCALE, StorageStackBE::getRotation, be -> index -> 0);
    }

    public static GridStackBER<SinglesStackBE> singles(BlockEntityRendererProvider.Context context) {
        return new GridStackBER<>(context, SinglesCubeIdx.GRID,
                CubeRenderHelper.CELL_RENDER_SCALE, SinglesStackBE::getRotation, be -> be::getCubeRotation);
    }

    @Override
    public CubeRenderHelper.GridRenderState createRenderState() {
        return new CubeRenderHelper.GridRenderState();
    }

    @Override
    public void extractRenderState(T be, CubeRenderHelper.GridRenderState state,
                                   float partialTick, Vec3 cameraPos,
                                   @Nullable ModelFeatureRenderer.CrumblingOverlay crumblingOverlay) {
        BlockEntityRenderer.super.extractRenderState(be, state, partialTick, cameraPos, crumblingOverlay);
        be.setRenderCache(CubeRenderHelper.extractGridItems(
                be.renderCache() instanceof CubeRenderHelper.GridCache cache ? cache : null,
                be.getItems(), be.getItems().getSlots(), grid,
                rotation.applyAsInt(be), scale, itemRotation.apply(be),
                be.getLevel(), be.getBlockPos(), cameraPos, state));
    }

    @Override
    public void submit(CubeRenderHelper.GridRenderState state, PoseStack pose,
                       SubmitNodeCollector collector, CameraRenderState camera) {
        CubeRenderHelper.submitGridItems(state, pose, collector);
    }
}
