package com.github.crittscott.somestacks.util;

import com.github.crittscott.somestacks.block.StorageStackBE;
import net.minecraft.core.BlockPos;

/**
 * The geometry of a Storage Stack's 3 x 3 x 3 grid: where each of the 27 cells sits in the block,
 * what box it occupies, and which one a player's reach ray hits first. Cells are separated by gaps,
 * so the grid does not fill the block even when every slot is occupied.
 *
 * <p>Positions are computed at the block's layout rotation, which reflects cell coordinates about
 * the grid's center rather than moving the contents between slots.
 */
public final class StorageCubeIdx {
    private StorageCubeIdx(){}

    public static final CubeGrid GRID = new CubeGrid(3, 4.5, 1, 6, 11);

    /** Cells along one axis of the grid. */
    public static final int GRID_EDGE = GRID.edge();

    /** Cells in one layer. */
    public static final int LAYER_SIZE = GRID.layerSize();

    /** Cells in one block, and therefore slots in one {@link StorageStackBE}. */
    public static final int CELLS = GRID.cells();

    /** Returns the nearest occupied cell intersected by the view ray, or {@code -1} on a miss. */
    public static int traceCubes(ViewRay ray, BlockPos blockPos, StorageStackBE be, int rotation) {
        return ray.nearestOccupied(be.getItems(), slot -> GRID.worldBox(slot, rotation, blockPos));
    }
}
