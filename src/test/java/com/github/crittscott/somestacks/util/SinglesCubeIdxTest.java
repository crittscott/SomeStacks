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
    void topLayerIsReportedInVisualCoordinates() {
        for (int lowerRotation = 0; lowerRotation < 4; lowerRotation++) {
            for (int storageColumn = 0; storageColumn < 16; storageColumn++) {
                boolean[] occupancy = new boolean[64];
                occupancy[SinglesCubeIdx.indexFromColumn(storageColumn, 3)] = true;

                boolean[] top = SinglesCubeIdx.topLayerOf(occupancy, lowerRotation);
                int expectedVisual =
                        SinglesCubeIdx.visualColumnFromStorage(storageColumn, lowerRotation);

                for (int visual = 0; visual < 16; visual++) {
                    assertEquals(visual == expectedVisual, top[visual]);
                }
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
}
