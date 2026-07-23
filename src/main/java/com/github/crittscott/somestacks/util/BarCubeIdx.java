package com.github.crittscott.somestacks.util;

import com.github.crittscott.somestacks.block.BarStackBE;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.items.IItemHandler;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class BarCubeIdx {
    private BarCubeIdx(){}

    // 8 layers alternating orientation: EW (0,2,4,6) and NS (1,3,5,7)
    // EW layers: 2 columns × 4 rows = 8 positions
    // NS layers: 4 columns × 2 rows = 8 positions
    // Total: 64 positions

    private static final double BAR_HEIGHT = 2.0;

    // EW layers (0,2,4,6): bars run East-West (long axis along X)
    private static final double[] EW_STARTS_X = {1.0, 9.0};
    private static final double[] EW_STARTS_Z = {0.5, 4.5, 8.5, 12.5};
    private static final double EW_WIDTH = 6.0;
    private static final double EW_DEPTH = 3.0;

    // NS layers (1,3,5,7): bars run North-South (long axis along Z)
    private static final double[] NS_STARTS_X = {0.5, 4.5, 8.5, 12.5};
    private static final double[] NS_STARTS_Z = {1.0, 9.0};
    private static final double NS_WIDTH = 3.0;
    private static final double NS_DEPTH = 6.0;

    // Y positions for 8 layers
    private static final double[] STARTS_Y = {0, 2, 4, 6, 8, 10, 12, 14};

    private static final int LAYER_SIZE = 8;
    private static final int TOP_LAYER_Y = 7;
    private static final int TOP_LAYER_START = TOP_LAYER_Y * LAYER_SIZE;

    private static boolean isEWLayer(int y) {
        return y % 2 == 0;
    }

    public static double startPixelX(int x, int y) {
        return isEWLayer(y) ? EW_STARTS_X[x] : NS_STARTS_X[x];
    }

    public static double startPixelY(int y) {
        return STARTS_Y[y];
    }

    public static double startPixelZ(int z, int y) {
        return isEWLayer(y) ? EW_STARTS_Z[z] : NS_STARTS_Z[z];
    }

    public static double barWidth(int y) {
        return isEWLayer(y) ? EW_WIDTH : NS_WIDTH;
    }

    public static double barHeight(int y) {
        return BAR_HEIGHT;
    }

    public static double barDepth(int y) {
        return isEWLayer(y) ? EW_DEPTH : NS_DEPTH;
    }

    public static int[] xyzFromIndex(int idx) {
        int y = idx / 8;
        int withinLayer = idx % 8;

        if (isEWLayer(y)) {
            int z = withinLayer / 2;
            int x = withinLayer % 2;
            return new int[]{x, y, z};
        } else {
            int z = withinLayer / 4;
            int x = withinLayer % 4;
            return new int[]{x, y, z};
        }
    }

    private static AABB getBarFootprint(int x, int y, int z) {
        double minX = startPixelX(x, y);
        double minZ = startPixelZ(z, y);
        double maxX = minX + barWidth(y);
        double maxZ = minZ + barDepth(y);
        return new AABB(minX, 0, minZ, maxX, 1, maxZ);
    }

    private static AABB getBarBox(int index, BlockPos blockPos) {
        int[] xyz = xyzFromIndex(index);

        double minX = blockPos.getX() + startPixelX(xyz[0], xyz[1]) / 16.0;
        double minY = blockPos.getY() + startPixelY(xyz[1]) / 16.0;
        double minZ = blockPos.getZ() + startPixelZ(xyz[2], xyz[1]) / 16.0;
        double maxX = minX + barWidth(xyz[1]) / 16.0;
        double maxY = minY + barHeight(xyz[1]) / 16.0;
        double maxZ = minZ + barDepth(xyz[1]) / 16.0;

        return new AABB(minX, minY, minZ, maxX, maxY, maxZ);
    }

    private static boolean footprintsOverlap(AABB a, AABB b) {
        return !(a.maxX <= b.minX || a.minX >= b.maxX ||
                a.maxZ <= b.minZ || a.minZ >= b.maxZ);
    }

    public static int traceCubes(Vec3 eyePos, Vec3 lookDir, BlockPos blockPos, BarStackBE be) {
        IItemHandler handler = be.getItems();

        Vec3 farPoint = eyePos.add(lookDir.scale(10.0));
        double closestDist = Double.MAX_VALUE;
        int closestIndex = -1;

        for (int i = 0; i < 64; i++) {
            ItemStack stack = handler.getStackInSlot(i);
            if (stack.isEmpty()) continue;

            AABB barBox = getBarBox(i, blockPos);
            Vec3 hit = barBox.clip(eyePos, farPoint).orElse(null);

            if (hit != null) {
                double dist = hit.distanceTo(eyePos);
                if (dist < closestDist) {
                    closestDist = dist;
                    closestIndex = i;
                }
            }
        }

        return closestIndex;
    }

    public static int traceAllPositions(Vec3 eyePos, Vec3 lookDir, BlockPos blockPos, IItemHandler handler) {
        Vec3 farPoint = eyePos.add(lookDir.scale(10.0));
        List<Hit> hits = new ArrayList<>();

        for (int i = 0; i < 64; i++) {
            AABB barBox = getBarBox(i, blockPos);
            Vec3 hitPos = barBox.clip(eyePos, farPoint).orElse(null);

            if (hitPos != null) {
                double dist = hitPos.distanceTo(eyePos);
                hits.add(new Hit(i, dist));
            }
        }

        if (hits.isEmpty()) return -1;

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

    /**
     * Occupancy of the top layer, the only layer that can hold up the Bar Stack above. Taken as a
     * snapshot so a cascade can carry it past the point where the block it came from is emptied and
     * removed from the world.
     */
    public static boolean[] topLayerOccupancy(IItemHandler handler) {
        boolean[] occupancy = new boolean[LAYER_SIZE];
        for (int i = 0; i < LAYER_SIZE; i++) {
            occupancy[i] = !handler.getStackInSlot(TOP_LAYER_START + i).isEmpty();
        }
        return occupancy;
    }

    /**
     * Whether the top layer beneath a block covers the footprint of {@code index}. Layer 7 runs
     * north-south and layer 0 east-west, so the alternation carries across a block boundary and the
     * ordinary footprint overlap describes support at the seam unchanged.
     */
    public static boolean seamSupports(int index, boolean[] seamBelow) {
        if (seamBelow == null) {
            return false;
        }

        int[] xyz = xyzFromIndex(index);
        AABB thisFootprint = getBarFootprint(xyz[0], xyz[1], xyz[2]);

        for (int i = 0; i < LAYER_SIZE; i++) {
            if (!seamBelow[i]) continue;

            int[] belowXYZ = xyzFromIndex(TOP_LAYER_START + i);
            AABB belowFootprint = getBarFootprint(belowXYZ[0], belowXYZ[1], belowXYZ[2]);

            if (footprintsOverlap(thisFootprint, belowFootprint)) {
                return true;
            }
        }

        return false;
    }

    public static boolean isGrounded(int index, IItemHandler handler) {
        return isGrounded(index, handler, null);
    }

    /**
     * Whether a bar rests on something. Layers above the bottom consult the layer beneath them in
     * the same block. The bottom layer consults {@code seamBelow}, the top-layer occupancy of the
     * Bar Stack underneath; a null seam means the block stands on the world rather than on another
     * Bar Stack, which grounds the bottom layer outright.
     */
    public static boolean isGrounded(int index, IItemHandler handler, boolean[] seamBelow) {
        int[] xyz = xyzFromIndex(index);
        int y = xyz[1];

        if (y == 0) return seamBelow == null || seamSupports(index, seamBelow);

        AABB thisFootprint = getBarFootprint(xyz[0], y, xyz[2]);

        int belowLayerStart = (y - 1) * LAYER_SIZE;
        for (int i = 0; i < LAYER_SIZE; i++) {
            int belowIndex = belowLayerStart + i;
            if (!handler.getStackInSlot(belowIndex).isEmpty()) {
                int[] belowXYZ = xyzFromIndex(belowIndex);
                AABB belowFootprint = getBarFootprint(belowXYZ[0], belowXYZ[1], belowXYZ[2]);

                if (footprintsOverlap(thisFootprint, belowFootprint)) {
                    return true;
                }
            }
        }

        return false;
    }

    public static int calculateDepositIndex(Vec3 eyePos, Vec3 lookDir, BlockPos blockPos) {
        Vec3 farPoint = eyePos.add(lookDir.scale(10.0));
        List<Hit> hits = new ArrayList<>();

        for (int i = 0; i < 64; i++) {
            AABB barBox = getBarBox(i, blockPos);
            Vec3 hitPos = barBox.clip(eyePos, farPoint).orElse(null);

            if (hitPos != null) {
                double dist = hitPos.distanceTo(eyePos);
                hits.add(new Hit(i, dist));
            }
        }

        if (hits.isEmpty()) return -1;

        hits.sort(Comparator.comparingDouble(h -> h.distance));
        return hits.get(hits.size() - 1).index;
    }

    private record Hit(int index, double distance) {
    }
}
