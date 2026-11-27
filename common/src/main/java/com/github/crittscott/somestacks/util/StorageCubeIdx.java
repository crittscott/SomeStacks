package com.github.crittscott.somestacks.util;

import com.github.crittscott.somestacks.block.StorageStackBE;

/**
 * The geometry of a Storage Stack's 3 x 3 x 3 grid: where each of the 27 cells sits in the block
 * and what box it occupies. Cells are separated by gaps, so the grid does not fill the block even
 * when every slot is occupied.
 *
 * <p>Positions are computed at the block's layout rotation, which reflects cell coordinates about
 * the grid's center rather than moving the contents between slots.
 */
public final class StorageCubeIdx {
    private StorageCubeIdx() {}

    /** Cells along one axis of the grid. */
    public static final int GRID_EDGE = 3;

    /** Cells in one layer. */
    public static final int LAYER_SIZE = GRID_EDGE * GRID_EDGE;

    /** Cells in one block, and therefore slots in one {@link StorageStackBE}. */
    public static final int CELLS = LAYER_SIZE * GRID_EDGE;

    /** Edge of one cell, in pixels. */
    public static final double CELL_PIXELS = 4.5;

    /** Cached coordinate transforms, bounds, and shapes for the Storage layout. */
    public static final CubeGrid GRID = new CubeGrid(GRID_EDGE, CELL_PIXELS, 1, 6, 11);
}
