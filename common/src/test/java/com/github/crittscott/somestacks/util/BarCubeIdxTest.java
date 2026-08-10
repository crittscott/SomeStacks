package com.github.crittscott.somestacks.util;

import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BarCubeIdxTest {

    @Test
    void everyIndexMapsToOneInRangeCoordinate() {
        Set<String> coordinates = new HashSet<>();

        for (int index = 0; index < 64; index++) {
            int[] xyz = BarCubeIdx.xyzFromIndex(index);
            int maxX = xyz[1] % 2 == 0 ? 2 : 4;
            int maxZ = xyz[1] % 2 == 0 ? 4 : 2;

            assertTrue(xyz[0] >= 0 && xyz[0] < maxX);
            assertTrue(xyz[1] >= 0 && xyz[1] < 8);
            assertTrue(xyz[2] >= 0 && xyz[2] < maxZ);
            assertTrue(coordinates.add(xyz[0] + "," + xyz[1] + "," + xyz[2]));
        }

        assertEquals(64, coordinates.size());
    }

    @Test
    void alternatingLayersUseTheExpectedDimensionsAndStayInsideTheBlock() {
        for (int index = 0; index < 64; index++) {
            int[] xyz = BarCubeIdx.xyzFromIndex(index);
            int y = xyz[1];

            assertEquals(y % 2 == 0 ? 6.0 : 3.0, BarCubeIdx.barWidth(y));
            assertEquals(y % 2 == 0 ? 3.0 : 6.0, BarCubeIdx.barDepth(y));
            assertEquals(2.0, BarCubeIdx.barHeight(y));

            assertTrue(BarCubeIdx.startPixelX(xyz[0], y) >= 0.0);
            assertTrue(BarCubeIdx.startPixelZ(xyz[2], y) >= 0.0);
            assertTrue(BarCubeIdx.startPixelX(xyz[0], y) + BarCubeIdx.barWidth(y) <= 16.0);
            assertTrue(BarCubeIdx.startPixelZ(xyz[2], y) + BarCubeIdx.barDepth(y) <= 16.0);
            assertTrue(BarCubeIdx.startPixelY(y) + BarCubeIdx.barHeight(y) <= 16.0);
        }
    }

    @Test
    void topLayerIsSlotsFiftySixThroughSixtyThree() {
        boolean[] occupancy = new boolean[64];
        occupancy[55] = true;
        occupancy[56] = true;
        occupancy[63] = true;

        boolean[] top = BarCubeIdx.topLayerOf(occupancy);

        assertTrue(top[0]);
        assertTrue(top[7]);
        for (int i = 1; i < 7; i++) {
            assertFalse(top[i]);
        }
    }

    @Test
    void standaloneBottomLayerIsGroundedButEmptySeamIsNot() {
        boolean[] occupancy = new boolean[64];

        for (int index = 0; index < 8; index++) {
            assertTrue(BarCubeIdx.isGroundedIn(occupancy, index, null));
            assertFalse(BarCubeIdx.isGroundedIn(
                    occupancy, index, BarCubeIdx.emptySeam()));
        }
    }

    @Test
    void seamSupportMatchesIndependentFootprintOverlap() {
        for (int bottomIndex = 0; bottomIndex < 8; bottomIndex++) {
            for (int topIndex = 0; topIndex < 8; topIndex++) {
                boolean[] seam = new boolean[8];
                seam[topIndex] = true;

                boolean expected = overlaps(bottomIndex, 56 + topIndex);
                assertEquals(expected, BarCubeIdx.seamSupports(bottomIndex, seam),
                        "bottom=" + bottomIndex + ", top=" + topIndex);
            }
        }
    }

    @Test
    void upperBarUsesOnlyTheImmediatelyLowerLayer() {
        boolean[] occupancy = new boolean[64];
        int target = 16;

        occupancy[0] = true;
        assertFalse(BarCubeIdx.isGroundedIn(occupancy, target, null));

        for (int below = 8; below < 16; below++) {
            if (overlaps(target, below)) {
                occupancy[below] = true;
                assertTrue(BarCubeIdx.isGroundedIn(occupancy, target, null));
                return;
            }
        }

        throw new AssertionError("No supporting position found");
    }

    @Test
    void emptyBlockTraceUsesTheFarthestIntersectedBar() {
        int result = BarCubeIdx.traceAllPositions(
                new ViewRay(new Vec3(0.25, 0.0625, -1.0), new Vec3(0.25, 0.0625, 2.0)),
                BlockPos.ZERO);

        assertTrue(result >= 0 && result < 8);
    }

    private static boolean overlaps(int firstIndex, int secondIndex) {
        int[] first = BarCubeIdx.xyzFromIndex(firstIndex);
        int[] second = BarCubeIdx.xyzFromIndex(secondIndex);

        double firstMinX = BarCubeIdx.startPixelX(first[0], first[1]);
        double firstMaxX = firstMinX + BarCubeIdx.barWidth(first[1]);
        double firstMinZ = BarCubeIdx.startPixelZ(first[2], first[1]);
        double firstMaxZ = firstMinZ + BarCubeIdx.barDepth(first[1]);

        double secondMinX = BarCubeIdx.startPixelX(second[0], second[1]);
        double secondMaxX = secondMinX + BarCubeIdx.barWidth(second[1]);
        double secondMinZ = BarCubeIdx.startPixelZ(second[2], second[1]);
        double secondMaxZ = secondMinZ + BarCubeIdx.barDepth(second[1]);

        return !(firstMaxX <= secondMinX || firstMinX >= secondMaxX
                || firstMaxZ <= secondMinZ || firstMinZ >= secondMaxZ);
    }
}
