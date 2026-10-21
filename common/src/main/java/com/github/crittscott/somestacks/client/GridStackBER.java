package com.github.crittscott.somestacks.client;

import com.github.crittscott.somestacks.block.SinglesStackBE;
import com.github.crittscott.somestacks.block.StackBlockEntity;
import com.github.crittscott.somestacks.block.StorageStackBE;
import com.github.crittscott.somestacks.util.CubeGrid;
import com.github.crittscott.somestacks.util.SinglesCubeIdx;
import com.github.crittscott.somestacks.util.StorageCubeIdx;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.world.phys.Vec3;

import java.util.function.Function;
import java.util.function.IntUnaryOperator;
import java.util.function.ToIntFunction;

/**
 * Loader-neutral renderer for the Storage and Singles cube grids. Each occupied cell is placed at
 * its corner, rotated with the block's layout, and handed to {@link CubeRenderHelper}, which applies
 * the item's own render profile. Singles items also carry a rotation of their own, applied on top of
 * the block's; rotations are read from the entity being rendered each frame.
 */
public final class GridStackBER<T extends StackBlockEntity> implements BlockEntityRenderer<T> {
    private final BlockRenderDispatcher blockRenderer;
    private final int slots;
    private final CubeGrid grid;
    private final float scale;
    private final ToIntFunction<T> rotation;
    private final Function<T, IntUnaryOperator> itemRotation;

    private GridStackBER(BlockEntityRendererProvider.Context context, int slots, CubeGrid grid, float scale,
                         ToIntFunction<T> rotation, Function<T, IntUnaryOperator> itemRotation) {
        this.blockRenderer = context.getBlockRenderDispatcher();
        this.slots = slots;
        this.grid = grid;
        this.scale = scale;
        this.rotation = rotation;
        this.itemRotation = itemRotation;
    }

    public static GridStackBER<StorageStackBE> storage(BlockEntityRendererProvider.Context context) {
        return new GridStackBER<>(context, StorageStackBE.SLOTS, StorageCubeIdx.GRID,
                CubeRenderHelper.STORAGE_CELL_RENDER_SCALE, StorageStackBE::getRotation, be -> index -> 0);
    }

    public static GridStackBER<SinglesStackBE> singles(BlockEntityRendererProvider.Context context) {
        return new GridStackBER<>(context, SinglesStackBE.SLOTS, SinglesCubeIdx.GRID,
                CubeRenderHelper.CELL_RENDER_SCALE, SinglesStackBE::getRotation, be -> be::getCubeRotation);
    }

    @Override
    public void render(T be, float partialTick, PoseStack pose, MultiBufferSource buffers, int light, int overlay, Vec3 cameraPos) {
        if (be.getLevel() != null) {
            CubeRenderHelper.renderGridItems(
                    be.getItems(), slots, grid, rotation.applyAsInt(be), scale, itemRotation.apply(be),
                    be.getLevel(), light, pose, buffers, blockRenderer);
        }
    }
}
