package com.github.crittscott.somestacks.util;

import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.Arrays;

/** Coordinate conversion, rotation, and cell boxes for a regular cubic item grid. */
public final class CubeGrid {
    private final int edge;
    private final int layerSize;
    private final int cells;
    private final int maxCoord;
    private final double cellPixels;
    private final int[] starts;

    public CubeGrid(int edge, double cellPixels, int... starts) {
        if (edge < 1 || starts.length != edge) {
            throw new IllegalArgumentException("A cube grid needs one start for each axis cell");
        }
        this.edge = edge;
        this.layerSize = edge * edge;
        this.cells = layerSize * edge;
        this.maxCoord = edge - 1;
        this.cellPixels = cellPixels;
        this.starts = Arrays.copyOf(starts, starts.length);
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
        return switch (Math.floorMod(rotation, 4)) {
            case 0 -> new int[]{x, y, z};
            case 1 -> new int[]{z, y, maxCoord - x};
            case 2 -> new int[]{maxCoord - x, y, maxCoord - z};
            case 3 -> new int[]{maxCoord - z, y, x};
            default -> throw new IllegalStateException();
        };
    }

    public AABB worldBox(int storageIndex, int rotation, BlockPos blockPos) {
        int[] storage = xyzFromIndex(storageIndex);
        int[] visual = rotateXYZ(storage[0], storage[1], storage[2], rotation);
        return worldBoxAtVisual(visual[0], visual[1], visual[2], blockPos);
    }

    public AABB worldBoxAtVisual(int x, int y, int z, BlockPos blockPos) {
        double minX = blockPos.getX() + startPixel(x) / 16.0;
        double minY = blockPos.getY() + startPixel(y) / 16.0;
        double minZ = blockPos.getZ() + startPixel(z) / 16.0;
        double size = cellPixels / 16.0;
        return new AABB(minX, minY, minZ, minX + size, minY + size, minZ + size);
    }

    public VoxelShape localShape(int storageIndex, int rotation) {
        int[] storage = xyzFromIndex(storageIndex);
        int[] visual = rotateXYZ(storage[0], storage[1], storage[2], rotation);
        double minX = startPixel(visual[0]) / 16.0;
        double minY = startPixel(visual[1]) / 16.0;
        double minZ = startPixel(visual[2]) / 16.0;
        double size = cellPixels / 16.0;
        return Shapes.box(minX, minY, minZ, minX + size, minY + size, minZ + size);
    }
}
