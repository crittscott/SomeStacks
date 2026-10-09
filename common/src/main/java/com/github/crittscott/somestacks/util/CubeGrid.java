package com.github.crittscott.somestacks.util;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.Arrays;

/** Coordinate conversion, rotation, and cell boxes for a regular cubic item grid. */
public final class CubeGrid {
    private final int edge;
    private final int layerSize;
    private final int cells;
    private final int maxCoord;
    private final int[] starts;
    private final AABB[] localBoxes;
    private final VoxelShape[] localShapes;
    private final int[][] rotatedIndices;

    public CubeGrid(int edge, double cellPixels, int... starts) {
        if (edge < 1 || starts.length != edge) {
            throw new IllegalArgumentException("A cube grid needs one start for each axis cell");
        }
        this.edge = edge;
        this.layerSize = edge * edge;
        this.cells = layerSize * edge;
        this.maxCoord = edge - 1;
        this.starts = Arrays.copyOf(starts, starts.length);
        this.localBoxes = new AABB[cells];
        this.localShapes = new VoxelShape[cells];
        this.rotatedIndices = new int[4][cells];
        for (int y = 0; y < edge; y++) {
            for (int z = 0; z < edge; z++) {
                for (int x = 0; x < edge; x++) {
                    int index = indexFromXYZ(x, y, z);
                    for (int rotation = 0; rotation < rotatedIndices.length; rotation++) {
                        int[] visual = rotateXYZ(x, y, z, rotation);
                        rotatedIndices[rotation][index] = indexFromXYZ(visual[0], visual[1], visual[2]);
                    }
                    double minX = starts[x] / 16.0;
                    double minY = starts[y] / 16.0;
                    double minZ = starts[z] / 16.0;
                    double size = cellPixels / 16.0;
                    localBoxes[index] = new AABB(
                            minX, minY, minZ, minX + size, minY + size, minZ + size);
                    localShapes[index] = Block.box(
                            starts[x], starts[y], starts[z],
                            starts[x] + cellPixels, starts[y] + cellPixels,
                            starts[z] + cellPixels);
                }
            }
        }
    }

    public int edge() {
        return edge;
    }

    public int layerSize() {
        return layerSize;
    }

    public int cells() {
        return cells;
    }

    public int startPixel(int coordinate) {
        return starts[coordinate];
    }

    /** Converts a bottom-up slot index to its x, y, z coordinates. */
    public int[] xyzFromIndex(int index) {
        int y = index / layerSize;
        int remainder = index % layerSize;
        int z = remainder / edge;
        int x = remainder % edge;
        return new int[]{x, y, z};
    }

    /** Rotates grid coordinates counterclockwise in quarter turns when viewed from above. */
    public int[] rotateXYZ(int x, int y, int z, int rotation) {
        return switch (QuarterTurns.normalize(rotation)) {
            case 0 -> new int[]{x, y, z};
            case 1 -> new int[]{z, y, maxCoord - x};
            case 2 -> new int[]{maxCoord - x, y, maxCoord - z};
            case 3 -> new int[]{maxCoord - z, y, x};
            default -> throw new IllegalStateException();
        };
    }

    public AABB worldBox(int storageIndex, int rotation, BlockPos blockPos) {
        return localBox(storageIndex, rotation).move(
                blockPos.getX(), blockPos.getY(), blockPos.getZ());
    }

    public AABB worldBoxAtVisual(int x, int y, int z, BlockPos blockPos) {
        return localBoxes[indexFromXYZ(x, y, z)].move(
                blockPos.getX(), blockPos.getY(), blockPos.getZ());
    }

    /** Returns the cached block-local bounds for one stored cell at the given layout rotation. */
    public AABB localBox(int storageIndex, int rotation) {
        return localBoxes[rotatedIndices[QuarterTurns.normalize(rotation)][storageIndex]];
    }

    public VoxelShape localShape(int storageIndex, int rotation) {
        return localShapes[rotatedIndices[QuarterTurns.normalize(rotation)][storageIndex]];
    }

    private int indexFromXYZ(int x, int y, int z) {
        return y * layerSize + z * edge + x;
    }
}
