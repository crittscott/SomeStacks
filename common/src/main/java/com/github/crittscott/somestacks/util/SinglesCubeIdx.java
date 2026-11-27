package com.github.crittscott.somestacks.util;

import com.github.crittscott.somestacks.block.SinglesStackBE;

import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.function.IntPredicate;

import javax.annotation.Nullable;

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

    /** Width of one cell in model pixels, shared by targeting, collision, and rendering. */
    public static final double CELL_PIXELS = 4.0;

    public static final CubeGrid GRID = new CubeGrid(4, CELL_PIXELS, 0, 4, 8, 12);

    /** Cells along one axis of the grid. */
    public static final int GRID_EDGE = GRID.edge();

    /** Cells in one layer. */
    public static final int LAYER_SIZE = GRID.layerSize();

    /** Layers in one block. */
    public static final int LAYERS = GRID_EDGE;

    /** Cells in one block, and therefore slots in one {@link SinglesStackBE}. */
    public static final int CELLS = GRID.cells();

    /** The layer a block hands items down from, and the only one that holds up the block above. */
    public static final int TOP_LAYER_Y = LAYERS - 1;

    /**
     * Whether a prospective Singles Stack could support an item at {@code index}. Since all of its
     * slots are still empty, only the bottom layer can be supported. It is grounded when there is
     * no Singles Stack below; otherwise its visual column must be occupied in {@code seamBelow}.
     * New blocks are unrotated, so {@code index} is already in the visual frame.
     */
    public static boolean freshBlockSupports(int index, boolean[] seamBelow) {
        return isGrounded(index, slot -> false, 0, seamBelow);
    }

    /**
     * Whether a cell has support. Cells above the bottom consult the cell beneath them in the same
     * storage column. The bottom layer consults {@code seamBelow}, the top-layer occupancy of the
     * Singles Stack underneath. A null seam means there is no Singles Stack below, so the bottom
     * layer is grounded.
     */
    public static boolean isGrounded(
            int index, SlotAccess handler, int blockRotation, boolean[] seamBelow) {
        return isGrounded(
                index, slot -> !handler.getStackInSlot(slot).isEmpty(), blockRotation, seamBelow);
    }

    /** Evaluates live or proposed local occupancy using the same visual seam rule. */
    public static boolean isGrounded(
            int index, IntPredicate occupied, int blockRotation, boolean[] seamBelow) {
        return isSupported(
                index,
                occupied,
                blockRotation,
                seamBelow == null ? null : column -> seamBelow[column]);
    }

    /** Support from local slots and the lower block's top layer expressed in visual columns. */
    public static boolean isSupported(
            int index, IntPredicate occupied, int blockRotation, @Nullable IntPredicate seamBelow) {
        if (index < LAYER_SIZE) {
            return seamBelow == null
                    || seamBelow.test(
                            visualColumnFromStorage(columnFromIndex(index), blockRotation));
        }
        return occupied.test(index - LAYER_SIZE);
    }

    /**
     * Whether the top layer beneath a block occupies the visual column containing {@code index}.
     */
    public static boolean seamSupports(int index, int blockRotation, boolean[] seamBelow) {
        if (seamBelow == null) {
            return false;
        }
        return isSupported(
                index % LAYER_SIZE, slot -> false, blockRotation, column -> seamBelow[column]);
    }

    /** Returns the block-local collision shape of one stored cell. */
    public static VoxelShape shapeFor(int storageIndex, int blockRotation) {
        return GRID.localShape(storageIndex, blockRotation);
    }

    /**
     * The storage column a visual column occupies under a block rotation, the inverse of {@link
     * #visualColumnFromStorage}. The four rotations form a cycle, so the inverse of {@code r} is
     * its normalized inverse.
     */
    public static int storageColumnFromVisual(int visualColumn, int blockRotation) {
        int[] storage =
                GRID.rotateXYZ(
                        visualColumn % GRID_EDGE,
                        0,
                        visualColumn / GRID_EDGE,
                        QuarterTurns.inverse(blockRotation));
        return storage[2] * GRID_EDGE + storage[0];
    }

    /**
     * Returns top-layer occupancy in visual columns, the coordinate system shared across a block
     * boundary even when adjacent blocks have different rotations.
     */
    public static boolean[] topLayerOccupancy(SlotAccess handler, int blockRotation) {
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
        int[] visual =
                GRID.rotateXYZ(
                        storageColumn % GRID_EDGE, 0, storageColumn / GRID_EDGE, blockRotation);
        return visual[2] * GRID_EDGE + visual[0];
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
