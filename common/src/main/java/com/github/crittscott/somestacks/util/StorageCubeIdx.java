package com.github.crittscott.somestacks.util;

import com.github.crittscott.somestacks.block.StorageStackBE;

/**
 * The geometry of a Storage Stack's 3 x 3 x 3 grid: where each of the 27 cells sits in the block
 * and what box it occupies. Cells are separated by gaps, so the grid does not fill the block even
 * when every slot is occupied.
 *
 * <p>{@link #GRID} gives cell boxes at the block's layout rotation, a quarter turn of cell
 * coordinates about the vertical axis, rather than moving the contents between slots.
 */
public final class StorageCubeIdx {
    private StorageCubeIdx() {}

    /** Cell width in model pixels, shared by targeting and rendering. */
    public static final double CELL_PIXELS = 4.5;

    public static final CubeGrid GRID = new CubeGrid(3, CELL_PIXELS, 1, 6, 11);

    /** Cells along one axis of the grid. */
    public static final int GRID_EDGE = GRID.edge();

    /** Cells in one layer. */
    public static final int LAYER_SIZE = GRID.layerSize();

    /** Cells in one block, and therefore slots in one {@link StorageStackBE}. */
    public static final int CELLS = GRID.cells();
}
