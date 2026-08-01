package com.github.crittscott.somestacks.util;

import com.github.crittscott.somestacks.block.BarStackBE;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraftforge.items.IItemHandler;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * The geometry of a Bar Stack's 64 bar positions: where each bar sits, what box it occupies, which
 * one a reach ray hits, and which bars hold up which.
 *
 * <p>Eight layers of eight alternate orientation. Even layers run East-West, two bars across and
 * four deep; odd layers run North-South, four across and two deep. A bar is supported when its
 * footprint overlaps one in the layer below, so support crosses between the two orientations rather
 * than following a single column, and the bottom layer rests on the seam with the block beneath,
 * which callers pass in as that block's top-layer occupancy.
 *
 * <p>Bars do not rotate with the block; their orientation belongs to the layer.
 */
public final class BarCubeIdx {
    private BarCubeIdx(){}

    private static final double BAR_HEIGHT = 2.0;

    /** Start pixels on X, in the even layers, where bars run East-West along their long axis. */
    private static final double[] EW_STARTS_X = {1.0, 9.0};

    /** Start pixels on Z, in the even layers. */
    private static final double[] EW_STARTS_Z = {0.5, 4.5, 8.5, 12.5};

    /** Bar span on X, in pixels, in the even layers. */
    private static final double EW_WIDTH = 6.0;

    /** Bar span on Z, in pixels, in the even layers. */
    private static final double EW_DEPTH = 3.0;

    /** Start pixels on X, in the odd layers, where bars run North-South along their long axis. */
    private static final double[] NS_STARTS_X = {0.5, 4.5, 8.5, 12.5};

    /** Start pixels on Z, in the odd layers. */
    private static final double[] NS_STARTS_Z = {1.0, 9.0};

    /** Bar span on X, in pixels, in the odd layers. */
    private static final double NS_WIDTH = 3.0;

    /** Bar span on Z, in pixels, in the odd layers. */
    private static final double NS_DEPTH = 6.0;

    /** Start pixel on Y of each layer, one entry per layer. */
    private static final double[] STARTS_Y = {0, 2, 4, 6, 8, 10, 12, 14};

    /** Bars in one layer, the two orientations laying out the same count differently. */
    public static final int LAYER_SIZE = EW_STARTS_X.length * EW_STARTS_Z.length;

    /** Layers in one block. */
    private static final int LAYERS = STARTS_Y.length;

    private static final int TOP_LAYER_Y = LAYERS - 1;
    private static final int TOP_LAYER_START = TOP_LAYER_Y * LAYER_SIZE;

    /**
     * Whether a Bar Stack that does not exist yet could take a bar at {@code index}, given the seam
     * it would stand on. Every cell of such a block is empty, so only the bottom layer can be
     * supported, and only by the seam.
     */
    public static boolean freshBlockSupports(int index, @Nullable boolean[] seamBelow) {
        return isGroundedIn(new boolean[BarStackBE.SLOTS], index, seamBelow);
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

    private static AABB getBarFootprint(int x, int y, int z) {
        double minX = startPixelX(x, y);
        double minZ = startPixelZ(z, y);
        double maxX = minX + barWidth(y);
        double maxZ = minZ + barDepth(y);
        return new AABB(minX, 0, minZ, maxX, 1, maxZ);
    }

    /**
     * Whether a bar rests on something. Layers above the bottom consult the layer beneath them in
     * the same block. The bottom layer consults {@code seamBelow}, the top-layer occupancy of the
     * Bar Stack underneath; a null seam means the block stands on the world rather than on another
     * Bar Stack, which grounds the bottom layer outright.
     */
    public static boolean isGrounded(int index, IItemHandler handler, @Nullable boolean[] seamBelow) {
        return isGroundedIn(occupancyOf(handler), index, seamBelow);
    }

    /**
     * {@link #isGrounded} against an occupancy snapshot rather than live slots, for callers that
     * settle a block in place or weigh a placement that has not happened yet.
     */
    public static boolean isGroundedIn(boolean[] occupancy, int index, @Nullable boolean[] seamBelow) {
        int[] xyz = xyzFromIndex(index);
        int y = xyz[1];

        if (y == 0) return seamBelow == null || seamSupports(index, seamBelow);

        AABB thisFootprint = getBarFootprint(xyz[0], y, xyz[2]);

        int belowLayerStart = (y - 1) * LAYER_SIZE;
        for (int i = 0; i < LAYER_SIZE; i++) {
            int belowIndex = belowLayerStart + i;
            if (occupancy[belowIndex]) {
                int[] belowXYZ = xyzFromIndex(belowIndex);
                AABB belowFootprint = getBarFootprint(belowXYZ[0], belowXYZ[1], belowXYZ[2]);

                if (footprintsOverlap(thisFootprint, belowFootprint)) {
                    return true;
                }
            }
        }

        return false;
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

    /** Block-local collision shape of one bar position. */
    public static VoxelShape shapeFor(int index) {
        int[] xyz = xyzFromIndex(index);
        double minX = startPixelX(xyz[0], xyz[1]) / 16.0;
        double minY = startPixelY(xyz[1]) / 16.0;
        double minZ = startPixelZ(xyz[2], xyz[1]) / 16.0;
        return Shapes.box(
                minX,
                minY,
                minZ,
                minX + barWidth(xyz[1]) / 16.0,
                minY + barHeight(xyz[1]) / 16.0,
                minZ + barDepth(xyz[1]) / 16.0);
    }

    /**
     * Deposit targeting for a Bar Stack that does not exist yet, where every cell is empty. Used to
     * decide, before the block is placed, which cell the deposit that follows would land in.
     */
    public static int traceAllPositions(ViewRay ray, BlockPos blockPos) {
        return traceAllPositions(ray, blockPos, null);
    }

    /**
     * The cell a deposit aims at: the last empty cell along the view ray before the first occupied
     * one, or the farthest intersected cell when the ray meets no bar. A null handler means every
     * cell is empty.
     */
    public static int traceAllPositions(ViewRay ray, BlockPos blockPos,
                                        @Nullable IItemHandler handler) {
        List<Hit> hits = new ArrayList<>();

        for (int i = 0; i < BarStackBE.SLOTS; i++) {
            AABB barBox = getBarBox(i, blockPos);
            Vec3 hitPos = barBox.clip(ray.eye(), ray.end()).orElse(null);

            if (hitPos != null) {
                double dist = hitPos.distanceTo(ray.eye());
                hits.add(new Hit(i, dist));
            }
        }

        if (hits.isEmpty()) return -1;

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

    public static int traceCubes(ViewRay ray, BlockPos blockPos, BarStackBE be) {
        IItemHandler handler = be.getItems();

        double closestDist = Double.MAX_VALUE;
        int closestIndex = -1;

        for (int i = 0; i < BarStackBE.SLOTS; i++) {
            ItemStack stack = handler.getStackInSlot(i);
            if (stack.isEmpty()) continue;

            AABB barBox = getBarBox(i, blockPos);
            Vec3 hit = barBox.clip(ray.eye(), ray.end()).orElse(null);

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

    public static int[] xyzFromIndex(int idx) {
        int y = idx / LAYER_SIZE;
        int withinLayer = idx % LAYER_SIZE;

        // A layer is indexed row by row, so its own count of bars along X divides the index.
        int barsAlongX = isEWLayer(y) ? EW_STARTS_X.length : NS_STARTS_X.length;
        int z = withinLayer / barsAlongX;
        int x = withinLayer % barsAlongX;
        return new int[]{x, y, z};
    }

    public static double barDepth(int y) {
        return isEWLayer(y) ? EW_DEPTH : NS_DEPTH;
    }

    public static double barHeight(int y) {
        return BAR_HEIGHT;
    }

    public static double barWidth(int y) {
        return isEWLayer(y) ? EW_WIDTH : NS_WIDTH;
    }

    /** A seam that holds nothing up, which is what a vanished Bar Stack leaves behind. */
    public static boolean[] emptySeam() {
        return new boolean[LAYER_SIZE];
    }

    private static boolean footprintsOverlap(AABB a, AABB b) {
        return !(a.maxX <= b.minX || a.minX >= b.maxX ||
                a.maxZ <= b.minZ || a.minZ >= b.maxZ);
    }

    private static boolean isEWLayer(int y) {
        return y % 2 == 0;
    }

    /** Snapshot of which of a block's cells hold a bar. */
    public static boolean[] occupancyOf(IItemHandler handler) {
        boolean[] occupancy = new boolean[BarStackBE.SLOTS];
        for (int i = 0; i < BarStackBE.SLOTS; i++) {
            occupancy[i] = !handler.getStackInSlot(i).isEmpty();
        }
        return occupancy;
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

    /** The top-layer slice of a block occupancy snapshot, in the form {@link #seamSupports} takes. */
    public static boolean[] topLayerOf(boolean[] occupancy) {
        boolean[] top = new boolean[LAYER_SIZE];
        System.arraycopy(occupancy, TOP_LAYER_START, top, 0, LAYER_SIZE);
        return top;
    }

    private record Hit(int index, double distance) {
    }
}
