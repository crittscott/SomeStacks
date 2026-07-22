package com.github.crittscott.somestacks.util;

import com.github.crittscott.somestacks.block.StorageStackBE;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.items.IItemHandler;

public final class StorageCubeIdx {
    private StorageCubeIdx(){}

    // Start pixels per axis for indices 0,1,2
    private static final int[] STARTS = {1, 6, 11};

    public static int startPixel(int i) {
        return STARTS[i];
    }

    public static int[] xyzFromIndex(int idx) {
        int y = idx / 9;
        int rem = idx % 9;
        int z = rem / 3;
        int x = rem % 3;
        return new int[]{x, y, z};
    }

    public static int[] rotateXYZ(int x, int y, int z, int rotation) {
        return switch (rotation % 4) {
            case 0 -> new int[]{x, y, z};
            case 1 -> new int[]{2 - z, y, x};
            case 2 -> new int[]{2 - x, y, 2 - z};
            case 3 -> new int[]{z, y, 2 - x};
            default -> new int[]{x, y, z};
        };
    }

    public static int traceCubes(Vec3 eyePos, Vec3 lookDir, BlockPos blockPos, StorageStackBE be, int rotation) {
        IItemHandler handler = be.getItems();

        Vec3 farPoint = eyePos.add(lookDir.scale(10.0)); // extend ray far past block
        double closestDist = Double.MAX_VALUE;
        int closestIndex = -1;

        for (int i = 0; i < 27; i++) {
            ItemStack stack = handler.getStackInSlot(i);
            if (stack.isEmpty()) continue; // skip empty cubes

            int[] xyz = xyzFromIndex(i);
            int[] visualXYZ = rotateXYZ(xyz[0], xyz[1], xyz[2], rotation);

            double minX = blockPos.getX() + startPixel(visualXYZ[0]) / 16.0;
            double minY = blockPos.getY() + startPixel(visualXYZ[1]) / 16.0;
            double minZ = blockPos.getZ() + startPixel(visualXYZ[2]) / 16.0;
            double maxX = minX + 4.0 / 16.0;
            double maxY = minY + 4.0 / 16.0;
            double maxZ = minZ + 4.0 / 16.0;

            AABB cubeBox = new AABB(minX, minY, minZ, maxX, maxY, maxZ);
            Vec3 hit = cubeBox.clip(eyePos, farPoint).orElse(null);

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
}
