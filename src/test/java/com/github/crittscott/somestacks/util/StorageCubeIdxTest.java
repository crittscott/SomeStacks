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
}
