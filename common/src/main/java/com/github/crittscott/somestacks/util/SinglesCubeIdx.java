package com.github.crittscott.somestacks.util;

import com.github.crittscott.somestacks.block.SinglesStackBE;

/**
 * Geometry and slot indexing shared by Singles Stack rendering, collision, ray targeting, and
 * support.
 *
 * <p>A block contains a 4 x 4 x 4 grid indexed from the bottom layer upward and row by row within
 * each layer. Each cell is supported by the cell directly below it. At a block boundary, callers
 * supply the lower block's top-layer occupancy as the supporting seam. Support follows visual
 * columns rather than storage indexes, so adjacent blocks remain aligned even when rotated
 * differently.
 */
public final class SinglesCubeIdx {
    private SinglesCubeIdx() {}

    /** Cells along one axis of the grid. */
    public static final int GRID_EDGE = 4;

    /** Cells in one layer. */
    public static final int LAYER_SIZE = GRID_EDGE * GRID_EDGE;

    /** Layers in one block. */
    public static final int LAYERS = GRID_EDGE;

    /** Cells in one block, and therefore slots in one {@link SinglesStackBE}. */
    public static final int CELLS = LAYER_SIZE * LAYERS;

    /** The layer a block hands items down from, and the only one that holds up the block above. */
    public static final int TOP_LAYER_Y = LAYERS - 1;

    /** Edge of one cell, in pixels. */
    public static final double CELL_PIXELS = 4.0;

    /** Cached coordinate transforms, bounds, and shapes for the Singles layout. */
    public static final CubeGrid GRID = new CubeGrid(GRID_EDGE, CELL_PIXELS, 0, 4, 8, 12);

    /**
     * Whether a prospective Singles Stack could support an item at {@code index}. Since all of its
     * slots are still empty, only the bottom layer can be supported. It is grounded when there is
     * no Singles Stack below; otherwise its visual column must be occupied in {@code seamBelow}.
     * New blocks are unrotated, so {@code index} is already in the visual frame.
     */
    public static boolean freshBlockSupports(int index, boolean[] seamBelow) {
        return index < LAYER_SIZE && (seamBelow == null || seamSupports(index, 0, seamBelow));
    }

    /**
     * Whether a cell has support. Cells above the bottom consult the cell beneath them in the same
     * storage column. The bottom layer consults {@code seamBelow}, the top-layer occupancy of the
     * Singles Stack underneath. A null seam means there is no Singles Stack below, so the bottom
     * layer is grounded.
     */
    public static boolean isGrounded(
            int index, StackItemStorage handler, int blockRotation, boolean[] seamBelow) {
        int[] xyz = GRID.xyzFromIndex(index);
        int y = xyz[1];

        if (y == 0) return seamBelow == null || seamSupports(index, blockRotation, seamBelow);

        int belowIndex = indexFromColumn(columnFromIndex(index), y - 1);

        return !handler.getStackInSlot(belowIndex).isEmpty();
    }

    /**
     * Applies {@link #isGrounded} to an occupancy snapshot rather than live slots. Column insertion
     * uses this to validate a proposed placement without mutating inventory.
     */
    public static boolean isGroundedIn(
            boolean[] occupancy, int index, int blockRotation, boolean[] seamBelow) {
        int y = GRID.xyzFromIndex(index)[1];

        if (y == 0) return seamBelow == null || seamSupports(index, blockRotation, seamBelow);

        return occupancy[indexFromColumn(columnFromIndex(index), y - 1)];
    }

    /**
     * Whether the top layer beneath a block occupies the visual column containing {@code index}.
     */
    public static boolean seamSupports(int index, int blockRotation, boolean[] seamBelow) {
        if (seamBelow == null) {
            return false;
        }
        return seamBelow[visualColumnFromStorage(columnFromIndex(index), blockRotation)];
    }

    /**
     * The storage column a visual column occupies under a block rotation, the inverse of {@link
     * #visualColumnFromStorage}. The four rotations form a cycle, so the inverse of {@code r} is
     * {@code (4 - r) % 4}.
     */
    public static int storageColumnFromVisual(int visualColumn, int blockRotation) {
        return GRID.visualIndex(
                visualColumn,
                (CubeGrid.ROTATIONS - Math.floorMod(blockRotation, CubeGrid.ROTATIONS))
                        % CubeGrid.ROTATIONS);
    }

    /** Reads the top cell beneath a bottom-layer cell in the same visual column. */
    public static boolean supportedBy(
            int index, int blockRotation, StackItemStorage below, int belowRotation) {
        int visualColumn = visualColumnFromStorage(columnFromIndex(index), blockRotation);
        int belowColumn = storageColumnFromVisual(visualColumn, belowRotation);
        return !below.getStackInSlot(indexFromColumn(belowColumn, TOP_LAYER_Y)).isEmpty();
    }

    /**
     * Returns top-layer occupancy in visual columns, the coordinate system shared across a block
     * boundary even when adjacent blocks have different rotations.
     */
    public static boolean[] topLayerOccupancy(StackItemStorage handler, int blockRotation) {
        boolean[] occupancy = new boolean[LAYER_SIZE];
        for (int column = 0; column < LAYER_SIZE; column++) {
            if (!handler.getStackInSlot(indexFromColumn(column, TOP_LAYER_Y)).isEmpty()) {
                occupancy[visualColumnFromStorage(column, blockRotation)] = true;
            }
        }
        return occupancy;
    }

    /**
     * The visual column a storage column occupies under a block rotation. Rotation permutes x and z
     * but never y, so columns always map to columns and only their horizontal identity moves.
     */
    public static int visualColumnFromStorage(int storageColumn, int blockRotation) {
        return GRID.visualIndex(storageColumn, blockRotation);
    }

    /** The storage column of a slot, the identity a vertical shift works along. */
    public static int columnFromIndex(int index) {
        return index % LAYER_SIZE;
    }

    /** The slot holding the cell at {@code y} in a storage column. */
    public static int indexFromColumn(int storageColumn, int y) {
        return y * LAYER_SIZE + storageColumn;
    }
}
