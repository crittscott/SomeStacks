package com.github.crittscott.somestacks.util;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SinglesCubeIdxTest {
    @Test
    void everyIndexMapsToOneInRangeCoordinate() {
        Set<String> coordinates = new HashSet<>();

        for (int index = 0; index < 64; index++) {
            int[] xyz = SinglesCubeIdx.xyzFromIndex(index);

            assertTrue(xyz[0] >= 0 && xyz[0] < 4);
            assertTrue(xyz[1] >= 0 && xyz[1] < 4);
            assertTrue(xyz[2] >= 0 && xyz[2] < 4);
            assertTrue(coordinates.add(xyz[0] + "," + xyz[1] + "," + xyz[2]));
        }

        assertEquals(64, coordinates.size());
    }

    @Test
    void rotationsAreBijectionsAndPreserveHeight() {
        for (int rotation = 0; rotation < 4; rotation++) {
            Set<String> rotated = new HashSet<>();

            for (int index = 0; index < 64; index++) {
                int[] xyz = SinglesCubeIdx.xyzFromIndex(index);
                int[] result = SinglesCubeIdx.rotateXYZ(xyz[0], xyz[1], xyz[2], rotation);

                assertEquals(xyz[1], result[1]);
                assertTrue(rotated.add(result[0] + "," + result[1] + "," + result[2]));
            }

            assertEquals(64, rotated.size());
        }
    }

    @Test
    void oneQuarterTurnGoesCounterClockwise() {
        // The northwest cell lands southwest, matching the direction of per-item rotation.
        assertArrayEquals(new int[]{0, 0, 3}, SinglesCubeIdx.rotateXYZ(0, 0, 0, 1));
    }

    @Test
    void fourQuarterTurnsRestoreEveryCoordinate() {
        for (int index = 0; index < 64; index++) {
            int[] original = SinglesCubeIdx.xyzFromIndex(index);
            int[] rotated = original.clone();

            for (int turn = 0; turn < 4; turn++) {
                rotated = SinglesCubeIdx.rotateXYZ(rotated[0], rotated[1], rotated[2], 1);
            }

            assertArrayEquals(original, rotated);
        }
    }

    @Test
    void visualAndStorageColumnsAreExactInverses() {
        for (int rotation = 0; rotation < 4; rotation++) {
            for (int storageColumn = 0; storageColumn < 16; storageColumn++) {
                int visual = SinglesCubeIdx.visualColumnFromStorage(storageColumn, rotation);
                assertEquals(storageColumn,
                        SinglesCubeIdx.storageColumnFromVisual(visual, rotation));
            }
        }
    }

    @Test
    void columnAndIndexMappingsRoundTrip() {
        for (int column = 0; column < 16; column++) {
            for (int y = 0; y < 4; y++) {
                int index = SinglesCubeIdx.indexFromColumn(column, y);
                assertEquals(column, SinglesCubeIdx.columnFromIndex(index));
                assertEquals(y, SinglesCubeIdx.xyzFromIndex(index)[1]);
            }
        }
    }

    @Test
    void standaloneBottomLayerIsGrounded() {
        boolean[] occupancy = new boolean[64];

        for (int column = 0; column < 16; column++) {
            assertTrue(SinglesCubeIdx.isGroundedIn(
                    occupancy, SinglesCubeIdx.indexFromColumn(column, 0), 0, null));
        }
    }

    @Test
    void stackedBottomLayerUsesVisualSeamColumn() {
        boolean[] occupancy = new boolean[64];

        for (int rotation = 0; rotation < 4; rotation++) {
            for (int storageColumn = 0; storageColumn < 16; storageColumn++) {
                int index = SinglesCubeIdx.indexFromColumn(storageColumn, 0);
                int supportingVisual =
                        SinglesCubeIdx.visualColumnFromStorage(storageColumn, rotation);
                boolean[] seam = new boolean[16];

                assertFalse(SinglesCubeIdx.isGroundedIn(
                        occupancy, index, rotation, seam));
                seam[supportingVisual] = true;
                assertTrue(SinglesCubeIdx.isGroundedIn(
                        occupancy, index, rotation, seam));
            }
        }
    }

    @Test
    void upperCellsUseOnlyTheCellImmediatelyBelow() {
        boolean[] occupancy = new boolean[64];
        int column = 6;
        int target = SinglesCubeIdx.indexFromColumn(column, 2);

        occupancy[SinglesCubeIdx.indexFromColumn(column, 0)] = true;
        assertFalse(SinglesCubeIdx.isGroundedIn(occupancy, target, 0, null));

        occupancy[SinglesCubeIdx.indexFromColumn(column, 1)] = true;
        assertTrue(SinglesCubeIdx.isGroundedIn(occupancy, target, 0, null));
    }

    @Test
    void cachedBoundsPreserveEveryCellAndRotation() {
        int[] starts = {0, 4, 8, 12};
        for (int rotation = 0; rotation < 4; rotation++) {
            for (int index = 0; index < 64; index++) {
                int x = index % 4, y = index / 16, z = index / 4 % 4;
                int visualX = switch (rotation) {
                    case 0 -> x;
                    case 1 -> z;
                    case 2 -> 3 - x;
                    default -> 3 - z;
                };
                int visualZ = switch (rotation) {
                    case 0 -> z;
                    case 1 -> 3 - x;
                    case 2 -> 3 - z;
                    default -> x;
                };
                var box = SinglesCubeIdx.localBox(index, rotation);
                assertEquals(starts[visualX] / 16.0, box.minX);
                assertEquals(starts[y] / 16.0, box.minY);
                assertEquals(starts[visualZ] / 16.0, box.minZ);
                assertEquals((starts[visualX] + 4.0) / 16.0, box.maxX);
                assertEquals((starts[y] + 4.0) / 16.0, box.maxY);
                assertEquals((starts[visualZ] + 4.0) / 16.0, box.maxZ);
                assertEquals(box, SinglesCubeIdx.shapeFor(index, rotation).bounds());
            }
        }
    }
}
