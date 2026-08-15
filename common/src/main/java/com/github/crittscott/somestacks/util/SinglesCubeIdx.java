package com.github.crittscott.somestacks.util;

import com.github.crittscott.somestacks.block.SinglesStackBE;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

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
    private SinglesCubeIdx(){}

    /** Cells along one axis of the grid. */
    public static final int GRID_EDGE = 4;

    /** Cells in one layer. */
    public static final int LAYER_SIZE = GRID_EDGE * GRID_EDGE;

    /** Layers in one block. */
    public static final int LAYERS = GRID_EDGE;

    /** The layer a block hands items down from, and the only one that holds up the block above. */
    public static final int TOP_LAYER_Y = LAYERS - 1;

    /** Edge of one cell, in pixels. */
    private static final double CELL_PIXELS = 4.0;

    /** The largest cell coordinate on an axis, which a rotation reflects about. */
    private static final int MAX_COORD = GRID_EDGE - 1;

    private static final int[] STARTS = {0, 4, 8, 12};

    /**
     * Whether a prospective Singles Stack could support an item at {@code index}. Since all of its
     * slots are still empty, only the bottom layer can be supported. It is grounded when there is no
     * Singles Stack below; otherwise its visual column must be occupied in {@code seamBelow}. New
     * blocks are unrotated, so {@code index} is already in the visual frame.
     */
    public static boolean freshBlockSupports(int index, boolean[] seamBelow) {
        return isGroundedIn(new boolean[SinglesStackBE.SLOTS], index, 0, seamBelow);
    }

    private static AABB getCubeBox(int visualX, int visualY, int visualZ, BlockPos blockPos) {
        double minX = blockPos.getX() + startPixel(visualX) / 16.0;
        double minY = blockPos.getY() + startPixel(visualY) / 16.0;
        double minZ = blockPos.getZ() + startPixel(visualZ) / 16.0;
        double maxX = minX + CELL_PIXELS / 16.0;
        double maxY = minY + CELL_PIXELS / 16.0;
        double maxZ = minZ + CELL_PIXELS / 16.0;

        return new AABB(minX, minY, minZ, maxX, maxY, maxZ);
    }

    /**
     * Whether a cell has support. Cells above the bottom consult the cell beneath them in the same
     * storage column. The bottom layer consults {@code seamBelow}, the top-layer occupancy of the
     * Singles Stack underneath. A null seam means there is no Singles Stack below, so the bottom
     * layer is grounded.
     */
    public static boolean isGrounded(int index, SlotAccess handler, int blockRotation, boolean[] seamBelow) {
        int[] xyz = xyzFromIndex(index);
        int y = xyz[1];

        if (y == 0) return seamBelow == null || seamSupports(index, blockRotation, seamBelow);

        int belowIndex = indexFromColumn(columnFromIndex(index), y - 1);

        return !handler.getStackInSlot(belowIndex).isEmpty();
    }

    /**
     * Applies {@link #isGrounded} to an occupancy snapshot rather than live slots. Column insertion
     * uses this to validate a proposed placement without mutating inventory.
     */
    public static boolean isGroundedIn(boolean[] occupancy, int index, int blockRotation, boolean[] seamBelow) {
        int y = xyzFromIndex(index)[1];

        if (y == 0) return seamBelow == null || seamSupports(index, blockRotation, seamBelow);

        return occupancy[indexFromColumn(columnFromIndex(index), y - 1)];
    }

    /** Whether the top layer beneath a block occupies the visual column containing {@code index}. */
    public static boolean seamSupports(int index, int blockRotation, boolean[] seamBelow) {
        if (seamBelow == null) {
            return false;
        }
        return seamBelow[visualColumnFromStorage(columnFromIndex(index), blockRotation)];
    }

    /** Returns the block-local collision shape of one stored cell. */
    public static VoxelShape shapeFor(int storageIndex, int blockRotation) {
        int[] storageXYZ = xyzFromIndex(storageIndex);
        int[] visualXYZ = rotateXYZ(
                storageXYZ[0], storageXYZ[1], storageXYZ[2], blockRotation);
        double minX = startPixel(visualXYZ[0]) / 16.0;
        double minY = startPixel(visualXYZ[1]) / 16.0;
        double minZ = startPixel(visualXYZ[2]) / 16.0;
        double size = CELL_PIXELS / 16.0;
        return Shapes.box(minX, minY, minZ, minX + size, minY + size, minZ + size);
    }

    /**
     * The storage column a visual column occupies under a block rotation, the inverse of
     * {@link #visualColumnFromStorage}. The four rotations form a cycle, so the inverse of
     * {@code r} is {@code (4 - r) % 4}.
     */
    public static int storageColumnFromVisual(int visualColumn, int blockRotation) {
        int[] storage = rotateXYZ(visualColumn % GRID_EDGE, 0, visualColumn / GRID_EDGE, (4 - blockRotation % 4) % 4);
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
     * Finds the deposit target for a Singles Stack that does not yet exist and therefore has no
     * occupied slots. Placement uses this to validate the initial deposit and its resulting
     * collision shape before adding the block to the world. New blocks are unrotated.
     */
    public static int traceAllPositions(ViewRay ray, BlockPos blockPos) {
        return traceAllPositions(ray, blockPos, null, 0);
    }

    /**
     * Finds the slot a deposit targets: the last empty slot along the view ray before the first
     * occupied one, or the farthest intersected slot if the ray meets no occupied item. A null
     * handler describes a prospective, entirely empty block.
     */
    public static int traceAllPositions(ViewRay ray, BlockPos blockPos,
                                        @Nullable SlotAccess handler, int blockRotation) {
        List<Hit> hits = new ArrayList<>();

        for (int storageIndex = 0; storageIndex < SinglesStackBE.SLOTS; storageIndex++) {
            int[] storageXYZ = xyzFromIndex(storageIndex);
            int[] visualXYZ = rotateXYZ(storageXYZ[0], storageXYZ[1], storageXYZ[2], blockRotation);

            AABB cubeBox = getCubeBox(visualXYZ[0], visualXYZ[1], visualXYZ[2], blockPos);
            Vec3 hitPos = cubeBox.clip(ray.eye(), ray.end()).orElse(null);

            if (hitPos != null) {
                double dist = hitPos.distanceTo(ray.eye());
                hits.add(new Hit(storageIndex, dist));
            }
        }

        if (hits.isEmpty()) {
            return -1;
        }

        hits.sort(Comparator.comparingDouble(h -> h.distance));

        int lastEmpty = -1;
        for (Hit hit : hits) {
            if (handler != null && !handler.getStackInSlot(hit.index).isEmpty()) {
                return lastEmpty;
            }
            lastEmpty = hit.index;
        }

        return lastEmpty;
    }

    /** Returns the nearest occupied cell intersected by the view ray, or {@code -1} on a miss. */
    public static int traceCubes(ViewRay ray, BlockPos blockPos, SinglesStackBE be) {
        SlotAccess handler = be.getItems();

        int blockRotation = be.getRotation();
        double closestDist = Double.MAX_VALUE;
        int closestIndex = -1;

        for (int storageIndex = 0; storageIndex < SinglesStackBE.SLOTS; storageIndex++) {
            ItemStack stack = handler.getStackInSlot(storageIndex);
            if (stack.isEmpty()) continue;

            int[] storageXYZ = xyzFromIndex(storageIndex);
            int[] visualXYZ = rotateXYZ(storageXYZ[0], storageXYZ[1], storageXYZ[2], blockRotation);

            AABB cubeBox = getCubeBox(visualXYZ[0], visualXYZ[1], visualXYZ[2], blockPos);
            Vec3 hit = cubeBox.clip(ray.eye(), ray.end()).orElse(null);

            if (hit != null) {
                double dist = hit.distanceTo(ray.eye());
                if (dist < closestDist) {
                    closestDist = dist;
                    closestIndex = storageIndex;
                }
            }
        }

        return closestIndex;
    }

    /**
     * The visual column a storage column occupies under a block rotation. Rotation permutes x and z
     * but never y, so columns always map to columns and only their horizontal identity moves.
     */
    public static int visualColumnFromStorage(int storageColumn, int blockRotation) {
        int[] visual = rotateXYZ(storageColumn % GRID_EDGE, 0, storageColumn / GRID_EDGE, blockRotation);
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

    /** Returns a snapshot of which slots in a block contain items. */
    public static boolean[] occupancyOf(SlotAccess handler) {
        boolean[] occupancy = new boolean[SinglesStackBE.SLOTS];
        for (int i = 0; i < SinglesStackBE.SLOTS; i++) {
            occupancy[i] = !handler.getStackInSlot(i).isEmpty();
        }
        return occupancy;
    }

    /**
     * The visual position of a stored cell under a block rotation. A rotation is that many quarter
     * turns counterclockwise seen from above, matching the direction of per-item rotation.
     */
    public static int[] rotateXYZ(int x, int y, int z, int rotation) {
        return switch (rotation % 4) {
            case 0 -> new int[]{x, y, z};
            case 1 -> new int[]{z, y, MAX_COORD - x};
            case 2 -> new int[]{MAX_COORD - x, y, MAX_COORD - z};
            case 3 -> new int[]{MAX_COORD - z, y, x};
            default -> new int[]{x, y, z};
        };
    }

    /** Returns a cell's origin on one axis in model pixels. */
    public static int startPixel(int i) {
        return STARTS[i];
    }

    /** Converts a slot index to its cell coordinates, indexed from the bottom layer upward. */
    public static int[] xyzFromIndex(int idx) {
        int y = idx / LAYER_SIZE;
        int rem = idx % LAYER_SIZE;
        int z = rem / GRID_EDGE;
        int x = rem % GRID_EDGE;
        return new int[]{x, y, z};
    }

    private record Hit(int index, double distance) {
    }
}
