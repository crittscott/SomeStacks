package com.github.crittscott.somestacks.util;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StorageCubeIdxTest {

    @Test
    void everyIndexMapsToOneInRangeCoordinate() {
        Set<String> coordinates = new HashSet<>();

        for (int index = 0; index < 27; index++) {
            int[] xyz = StorageCubeIdx.xyzFromIndex(index);

            assertTrue(xyz[0] >= 0 && xyz[0] < 3);
            assertTrue(xyz[1] >= 0 && xyz[1] < 3);
            assertTrue(xyz[2] >= 0 && xyz[2] < 3);
            assertTrue(coordinates.add(xyz[0] + "," + xyz[1] + "," + xyz[2]));
        }

        assertEquals(27, coordinates.size());
    }

    @Test
    void rotationsAreBijectionsAndPreserveHeight() {
        for (int rotation = 0; rotation < 4; rotation++) {
            Set<String> rotated = new HashSet<>();

            for (int index = 0; index < 27; index++) {
                int[] xyz = StorageCubeIdx.xyzFromIndex(index);
                int[] result = StorageCubeIdx.rotateXYZ(xyz[0], xyz[1], xyz[2], rotation);

                assertEquals(xyz[1], result[1]);
                assertTrue(rotated.add(result[0] + "," + result[1] + "," + result[2]));
            }

            assertEquals(27, rotated.size());
        }
    }

    @Test
    void oneQuarterTurnGoesCounterClockwise() {
        // The northwest cell lands southwest, matching the direction of per-item rotation.
        assertArrayEquals(new int[]{0, 0, 2}, StorageCubeIdx.rotateXYZ(0, 0, 0, 1));
    }

    @Test
    void fourQuarterTurnsRestoreEveryCoordinate() {
        for (int index = 0; index < 27; index++) {
            int[] original = StorageCubeIdx.xyzFromIndex(index);
            int[] rotated = original.clone();

            for (int turn = 0; turn < 4; turn++) {
                rotated = StorageCubeIdx.rotateXYZ(rotated[0], rotated[1], rotated[2], 1);
            }

            assertArrayEquals(original, rotated);
        }
    }

    @Test
    void cachedBoundsPreserveEveryCellAndRotation() {
        int[] starts = {1, 6, 11};
        for (int rotation = 0; rotation < 4; rotation++) {
            for (int index = 0; index < 27; index++) {
                int x = index % 3, y = index / 9, z = index / 3 % 3;
                int visualX = switch (rotation) {
                    case 0 -> x;
                    case 1 -> z;
                    case 2 -> 2 - x;
                    default -> 2 - z;
                };
                int visualZ = switch (rotation) {
                    case 0 -> z;
                    case 1 -> 2 - x;
                    case 2 -> 2 - z;
                    default -> x;
                };
                var box = StorageCubeIdx.localBox(index, rotation);
                assertEquals(starts[visualX] / 16.0, box.minX);
                assertEquals(starts[y] / 16.0, box.minY);
                assertEquals(starts[visualZ] / 16.0, box.minZ);
                assertEquals((starts[visualX] + 4.5) / 16.0, box.maxX);
                assertEquals((starts[y] + 4.5) / 16.0, box.maxY);
                assertEquals((starts[visualZ] + 4.5) / 16.0, box.maxZ);
            }
        }
    }
}
