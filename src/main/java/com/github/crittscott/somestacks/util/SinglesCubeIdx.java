package com.github.crittscott.somestacks.util;

import com.github.crittscott.somestacks.block.SinglesStackBE;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraftforge.items.IItemHandler;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

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

    /** The largest cell coordinate on an axis, which a rotation reflects about. */
    private static final int MAX_COORD = GRID_EDGE - 1;

    /** Edge of one cell, in pixels. */
    private static final double CELL_PIXELS = 4.0;

    private static final int[] STARTS = {0, 4, 8, 12};

    public static int startPixel(int i) {
        return STARTS[i];
    }

    public static int[] xyzFromIndex(int idx) {
        int y = idx / LAYER_SIZE;
        int rem = idx % LAYER_SIZE;
        int z = rem / GRID_EDGE;
        int x = rem % GRID_EDGE;
        return new int[]{x, y, z};
    }

    public static int[] rotateXYZ(int x, int y, int z, int rotation) {
        return switch (rotation % 4) {
            case 0 -> new int[]{x, y, z};
            case 1 -> new int[]{MAX_COORD - z, y, x};
            case 2 -> new int[]{MAX_COORD - x, y, MAX_COORD - z};
            case 3 -> new int[]{z, y, MAX_COORD - x};
            default -> new int[]{x, y, z};
        };
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

    /** Block-local collision shape of one stored cell. */
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

    public static int traceCubes(ViewRay ray, BlockPos blockPos, SinglesStackBE be) {
        IItemHandler handler = be.getItems();

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

    public static int traceAllPositions(ViewRay ray, BlockPos blockPos, IItemHandler handler, int blockRotation) {
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
            boolean occupied = !handler.getStackInSlot(hit.index).isEmpty();
            if (occupied) {
                return lastEmpty;
            }
            lastEmpty = hit.index;
        }

        return lastEmpty;
    }

    /** The slot holding the cell at {@code y} in a storage column. */
    public static int indexFromColumn(int storageColumn, int y) {
        return y * LAYER_SIZE + storageColumn;
    }

    /** The storage column of a slot, the identity a vertical shift works along. */
    public static int columnFromIndex(int index) {
        return index % LAYER_SIZE;
    }

    /**
     * The visual column a storage column occupies under a block rotation. Rotation permutes x and z
     * but never y, so columns always map to columns and only their horizontal identity moves.
     */
    public static int visualColumnFromStorage(int storageColumn, int blockRotation) {
        int[] visual = rotateXYZ(storageColumn % GRID_EDGE, 0, storageColumn / GRID_EDGE, blockRotation);
        return visual[2] * GRID_EDGE + visual[0];
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
     * Occupancy of the top layer, the only layer that can hold up the Singles Stack above, recorded
     * in visual columns so it means the same thing to a block above of any rotation. Taken as a
     * snapshot so it stays usable once the block it came from is gone.
     */
    public static boolean[] topLayerOccupancy(IItemHandler handler, int blockRotation) {
        boolean[] occupancy = new boolean[LAYER_SIZE];
        for (int column = 0; column < LAYER_SIZE; column++) {
            if (!handler.getStackInSlot(indexFromColumn(column, TOP_LAYER_Y)).isEmpty()) {
                occupancy[visualColumnFromStorage(column, blockRotation)] = true;
            }
        }
        return occupancy;
    }

    /** Snapshot of which of a block's cells hold an item. */
    public static boolean[] occupancyOf(IItemHandler handler) {
        boolean[] occupancy = new boolean[SinglesStackBE.SLOTS];
        for (int i = 0; i < SinglesStackBE.SLOTS; i++) {
            occupancy[i] = !handler.getStackInSlot(i).isEmpty();
        }
        return occupancy;
    }

    /**
     * The top-layer slice of a block occupancy snapshot, in the visual columns
     * {@link #seamSupports} takes.
     */
    public static boolean[] topLayerOf(boolean[] occupancy, int blockRotation) {
        boolean[] top = new boolean[LAYER_SIZE];
        for (int column = 0; column < LAYER_SIZE; column++) {
            if (occupancy[indexFromColumn(column, TOP_LAYER_Y)]) {
                top[visualColumnFromStorage(column, blockRotation)] = true;
            }
        }
        return top;
    }

    /** Whether the top layer beneath a block occupies the visual column {@code index} stands in. */
    public static boolean seamSupports(int index, int blockRotation, boolean[] seamBelow) {
        if (seamBelow == null) {
            return false;
        }
        return seamBelow[visualColumnFromStorage(columnFromIndex(index), blockRotation)];
    }

    /**
     * Whether a cell rests on something. Cells above the bottom consult the cell under them in the
     * same column. The bottom layer consults {@code seamBelow}, the top-layer occupancy of the
     * Singles Stack underneath; a null seam means the block stands on the world rather than on
     * another Singles Stack, which grounds the bottom layer outright.
     */
    public static boolean isGrounded(int index, IItemHandler handler, int blockRotation, boolean[] seamBelow) {
        int[] xyz = xyzFromIndex(index);
        int y = xyz[1];

        if (y == 0) return seamBelow == null || seamSupports(index, blockRotation, seamBelow);

        int belowIndex = indexFromColumn(columnFromIndex(index), y - 1);

        return !handler.getStackInSlot(belowIndex).isEmpty();
    }

    /**
     * {@link #isGrounded} against an occupancy snapshot rather than live slots, for callers that
     * weigh a run of placements before any of them happens.
     */
    public static boolean isGroundedIn(boolean[] occupancy, int index, int blockRotation, boolean[] seamBelow) {
        int y = xyzFromIndex(index)[1];

        if (y == 0) return seamBelow == null || seamSupports(index, blockRotation, seamBelow);

        return occupancy[indexFromColumn(columnFromIndex(index), y - 1)];
    }

    /**
     * Whether a Singles Stack that does not exist yet could take an item at {@code index}, given the
     * seam it would stand on. Every cell of such a block is empty, so only the bottom layer can be
     * supported, and only by the seam. A newly placed block is unrotated, so {@code index} is read in
     * the visual frame.
     */
    public static boolean freshBlockSupports(int index, boolean[] seamBelow) {
        return isGroundedIn(new boolean[SinglesStackBE.SLOTS], index, 0, seamBelow);
    }

    public static int calculateDepositIndex(ViewRay ray, BlockPos blockPos, int blockRotation) {
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
        return hits.get(hits.size() - 1).index;
    }

    private record Hit(int index, double distance) {
    }
}
