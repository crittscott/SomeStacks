package com.github.crittscott.somestacks.util;

import com.github.crittscott.somestacks.block.BarStackBE;
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
 * Geometry and slot indexing shared by Bar Stack rendering, collision, ray targeting, and support.
 *
 * <p>A block contains eight layers of eight bars. Even layers run east-west, two bars across and
 * four deep; odd layers run north-south, four across and two deep. Slots are indexed from the bottom
 * layer upward and row by row within each layer.
 *
 * <p>A bar is supported when its footprint overlaps a bar in the layer below. This makes support
 * cross between the alternating orientations instead of following fixed columns. At a block
 * boundary, callers supply the lower block's top-layer occupancy as the supporting seam.
 *
 * <p>Bars do not rotate with the block; their orientation belongs to the layer.
 */
public final class BarCubeIdx {
    private BarCubeIdx(){}

    // Bar geometry is expressed in model pixels (1/16 of a block).
    private static final double BAR_HEIGHT = 2.0;

    /** X origins in even layers, where the bars' long axes run east-west. */
    private static final double[] EW_STARTS_X = {1.0, 9.0};

    /** Z origins in even layers. */
    private static final double[] EW_STARTS_Z = {0.5, 4.5, 8.5, 12.5};

    /** X span in even layers. */
    private static final double EW_WIDTH = 6.0;

    /** Z span in even layers. */
    private static final double EW_DEPTH = 3.0;

    /** X origins in odd layers, where the bars' long axes run north-south. */
    private static final double[] NS_STARTS_X = {0.5, 4.5, 8.5, 12.5};

    /** Z origins in odd layers. */
    private static final double[] NS_STARTS_Z = {1.0, 9.0};

    /** X span in odd layers. */
    private static final double NS_WIDTH = 3.0;

    /** Z span in odd layers. */
    private static final double NS_DEPTH = 6.0;

    /** Y origin of each layer. */
    private static final double[] STARTS_Y = {0, 2, 4, 6, 8, 10, 12, 14};

    /** Slots per layer; the two orientations arrange the same count differently. */
    public static final int LAYER_SIZE = EW_STARTS_X.length * EW_STARTS_Z.length;

    /** Layers per block. */
    private static final int LAYERS = STARTS_Y.length;

    private static final int TOP_LAYER_Y = LAYERS - 1;
    private static final int TOP_LAYER_START = TOP_LAYER_Y * LAYER_SIZE;

    /** Slots in one block, and therefore slots in one {@link BarStackBE}. */
    public static final int CELLS = LAYER_SIZE * LAYERS;

    /**
     * Whether a prospective Bar Stack could support a bar at {@code index}. Since all of its slots
     * are still empty, only the bottom layer can be supported. It is grounded when there is no Bar
     * Stack below; otherwise its footprint must overlap {@code seamBelow}.
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
     * Whether a bar has support. Layers above the bottom consult the layer beneath them in the same
     * block. The bottom layer consults {@code seamBelow}, the top-layer occupancy of the Bar Stack
     * underneath. A null seam means there is no Bar Stack below, so the bottom layer is grounded.
     */
    public static boolean isGrounded(int index, SlotAccess handler, @Nullable boolean[] seamBelow) {
        return isGroundedIn(occupancyOf(handler), index, seamBelow);
    }

    /**
     * Applies {@link #isGrounded} to an occupancy snapshot rather than live slots. Settling and
     * placement simulation use snapshots to evaluate a proposed state without mutating inventory.
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
     * Whether the top layer beneath a block overlaps the footprint of {@code index}. Layer 7 runs
     * north-south and layer 0 east-west, so the alternating pattern continues across a block
     * boundary and uses the same footprint test as support within a block.
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

    /** Returns the block-local collision shape of one bar slot. */
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
     * Finds the deposit target for a Bar Stack that does not yet exist and therefore has no occupied
     * slots. Placement uses this to validate the initial deposit and its resulting collision shape
     * before adding the block to the world.
     */
    public static int traceAllPositions(ViewRay ray, BlockPos blockPos) {
        return traceAllPositions(ray, blockPos, null);
    }

    /**
     * Finds the slot a deposit targets: the last empty slot along the view ray before the first
     * occupied one, or the farthest intersected slot if the ray meets no occupied bar. A null handler
     * describes a prospective, entirely empty block.
     */
    public static int traceAllPositions(ViewRay ray, BlockPos blockPos,
                                        @Nullable SlotAccess handler) {
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

    /** Returns the nearest occupied bar intersected by the view ray, or {@code -1} on a miss. */
    public static int traceCubes(ViewRay ray, BlockPos blockPos, BarStackBE be) {
        SlotAccess handler = be.getItems();

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

    /** Converts a slot index to its within-layer X and Z indexes and its bottom-based layer index. */
    public static int[] xyzFromIndex(int idx) {
        int y = idx / LAYER_SIZE;
        int withinLayer = idx % LAYER_SIZE;

        // A layer is indexed row by row, so its own count of bars along X divides the index.
        int barsAlongX = isEWLayer(y) ? EW_STARTS_X.length : NS_STARTS_X.length;
        int z = withinLayer / barsAlongX;
        int x = withinLayer % barsAlongX;
        return new int[]{x, y, z};
    }

    /** Returns the bar's Z span in model pixels for layer {@code y}. */
    public static double barDepth(int y) {
        return isEWLayer(y) ? EW_DEPTH : NS_DEPTH;
    }

    /** Returns a bar's constant Y span in model pixels; {@code y} keeps all span helpers layer-indexed. */
    public static double barHeight(int y) {
        return BAR_HEIGHT;
    }

    /** Returns the bar's X span in model pixels for layer {@code y}. */
    public static double barWidth(int y) {
        return isEWLayer(y) ? EW_WIDTH : NS_WIDTH;
    }

    /** Returns an empty seam used to continue a support cascade after a Bar Stack is removed. */
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

    /** Returns a snapshot of which slots in a block contain bars. */
    public static boolean[] occupancyOf(SlotAccess handler) {
        boolean[] occupancy = new boolean[BarStackBE.SLOTS];
        for (int i = 0; i < BarStackBE.SLOTS; i++) {
            occupancy[i] = !handler.getStackInSlot(i).isEmpty();
        }
        return occupancy;
    }

    /** Returns a bar's X origin in model pixels from its within-layer X index and layer. */
    public static double startPixelX(int x, int y) {
        return isEWLayer(y) ? EW_STARTS_X[x] : NS_STARTS_X[x];
    }

    /** Returns a layer's Y origin in model pixels. */
    public static double startPixelY(int y) {
        return STARTS_Y[y];
    }

    /** Returns a bar's Z origin in model pixels from its within-layer Z index and layer. */
    public static double startPixelZ(int z, int y) {
        return isEWLayer(y) ? EW_STARTS_Z[z] : NS_STARTS_Z[z];
    }

    /**
     * Returns the top layer's occupancy, which is the supporting seam for the Bar Stack above. The
     * snapshot lets a support cascade retain the seam after its source block has been emptied and
     * removed from the world.
     */
    public static boolean[] topLayerOccupancy(SlotAccess handler) {
        boolean[] occupancy = new boolean[LAYER_SIZE];
        for (int i = 0; i < LAYER_SIZE; i++) {
            occupancy[i] = !handler.getStackInSlot(TOP_LAYER_START + i).isEmpty();
        }
        return occupancy;
    }

    private record Hit(int index, double distance) {
    }
}
