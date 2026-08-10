package com.github.crittscott.somestacks.util;

import com.github.crittscott.somestacks.block.StorageStackBE;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

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

    /** Cells along one axis of the grid. */
    public static final int GRID_EDGE = 3;

    /** Cells in one layer. */
    public static final int LAYER_SIZE = GRID_EDGE * GRID_EDGE;


    /** Edge of one cell, in pixels. */
    private static final double CELL_PIXELS = 4.0;

    /** The largest cell coordinate on an axis, which a rotation reflects about. */
    private static final int MAX_COORD = GRID_EDGE - 1;

    /** Start pixel of each cell on an axis, indexed by cell coordinate. */
    private static final int[] STARTS = {1, 6, 11};

    /** Returns the nearest occupied cell intersected by the view ray, or {@code -1} on a miss. */
    public static int traceCubes(ViewRay ray, BlockPos blockPos, StorageStackBE be, int rotation) {
        SlotAccess handler = be.getItems();

        double closestDist = Double.MAX_VALUE;
        int closestIndex = -1;

        for (int i = 0; i < StorageStackBE.SLOTS; i++) {
            ItemStack stack = handler.getStackInSlot(i);
            if (stack.isEmpty()) continue;

            int[] xyz = xyzFromIndex(i);
            int[] visualXYZ = rotateXYZ(xyz[0], xyz[1], xyz[2], rotation);

            double minX = blockPos.getX() + startPixel(visualXYZ[0]) / 16.0;
            double minY = blockPos.getY() + startPixel(visualXYZ[1]) / 16.0;
            double minZ = blockPos.getZ() + startPixel(visualXYZ[2]) / 16.0;
            double maxX = minX + CELL_PIXELS / 16.0;
            double maxY = minY + CELL_PIXELS / 16.0;
            double maxZ = minZ + CELL_PIXELS / 16.0;

            AABB cubeBox = new AABB(minX, minY, minZ, maxX, maxY, maxZ);
            Vec3 hit = cubeBox.clip(ray.eye(), ray.end()).orElse(null);

            if (hit != null) {
                double dist = hit.distanceTo(ray.eye());
                if (dist < closestDist) {
                    closestDist = dist;
                    closestIndex = i;
                }
            }
        }

        return closestIndex;
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
}
