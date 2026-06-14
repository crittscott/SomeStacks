package com.github.crittscott.somestacks.util;

import com.github.crittscott.somestacks.block.SinglesStackBE;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.items.IItemHandler;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class SinglesCubeIdx {
    private SinglesCubeIdx(){}

    private static final int[] STARTS = {0, 4, 8, 12};

    public static int startPixel(int i) {
        return STARTS[i];
    }

    public static int[] xyzFromIndex(int idx) {
        int y = idx / 16;
        int rem = idx % 16;
        int z = rem / 4;
        int x = rem % 4;
        return new int[]{x, y, z};
    }

    public static int[] rotateXYZ(int x, int y, int z, int rotation) {
        return switch (rotation % 4) {
            case 0 -> new int[]{x, y, z};
            case 1 -> new int[]{3 - z, y, x};
            case 2 -> new int[]{3 - x, y, 3 - z};
            case 3 -> new int[]{z, y, 3 - x};
            default -> new int[]{x, y, z};
        };
    }

    private static AABB getCubeBox(int visualX, int visualY, int visualZ, BlockPos blockPos) {
        double minX = blockPos.getX() + startPixel(visualX) / 16.0;
        double minY = blockPos.getY() + startPixel(visualY) / 16.0;
        double minZ = blockPos.getZ() + startPixel(visualZ) / 16.0;
        double maxX = minX + 4.0 / 16.0;
        double maxY = minY + 4.0 / 16.0;
        double maxZ = minZ + 4.0 / 16.0;

        return new AABB(minX, minY, minZ, maxX, maxY, maxZ);
    }

    public static int traceCubes(Vec3 eyePos, Vec3 lookDir, BlockPos blockPos, SinglesStackBE be) {
        IItemHandler handler = be.getCapability(ForgeCapabilities.ITEM_HANDLER).orElse(null);
        if (handler == null) return -1;

        int blockRotation = be.getRotation();
        Vec3 farPoint = eyePos.add(lookDir.scale(10.0));
        double closestDist = Double.MAX_VALUE;
        int closestIndex = -1;

        for (int storageIndex = 0; storageIndex < 64; storageIndex++) {
            ItemStack stack = handler.getStackInSlot(storageIndex);
            if (stack.isEmpty()) continue;

            int[] storageXYZ = xyzFromIndex(storageIndex);
            int[] visualXYZ = rotateXYZ(storageXYZ[0], storageXYZ[1], storageXYZ[2], blockRotation);

            AABB cubeBox = getCubeBox(visualXYZ[0], visualXYZ[1], visualXYZ[2], blockPos);
            Vec3 hit = cubeBox.clip(eyePos, farPoint).orElse(null);

            if (hit != null) {
                double dist = hit.distanceTo(eyePos);
                if (dist < closestDist) {
                    closestDist = dist;
                    closestIndex = storageIndex;
                }
            }
        }

        return closestIndex;
    }

    public static int traceAllPositions(Vec3 eyePos, Vec3 lookDir, BlockPos blockPos, IItemHandler handler, int blockRotation) {
        Vec3 farPoint = eyePos.add(lookDir.scale(10.0));

        List<Hit> hits = new ArrayList<>();

        for (int storageIndex = 0; storageIndex < 64; storageIndex++) {
            int[] storageXYZ = xyzFromIndex(storageIndex);
            int[] visualXYZ = rotateXYZ(storageXYZ[0], storageXYZ[1], storageXYZ[2], blockRotation);

            AABB cubeBox = getCubeBox(visualXYZ[0], visualXYZ[1], visualXYZ[2], blockPos);
            Vec3 hitPos = cubeBox.clip(eyePos, farPoint).orElse(null);

            if (hitPos != null) {
                double dist = hitPos.distanceTo(eyePos);
                hits.add(new Hit(storageIndex, dist));
            }
        }

        if (hits.isEmpty()) {
            return -1;
        }

        hits.sort(Comparator.comparingDouble(h -> h.distance));

        int lastEmpty = -1;
        for (Hit hit : hits) {
            boolean occupied = !handler.getStackInSlot(hit.index).isEmpty();
            if (occupied) {
                return lastEmpty;
            }
            lastEmpty = hit.index;
        }

        return lastEmpty;
    }

    public static boolean isGrounded(int index, IItemHandler handler) {
        int[] xyz = xyzFromIndex(index);
        int y = xyz[1];

        if (y == 0) return true;

        int x = xyz[0];
        int z = xyz[2];
        int belowIndex = (y - 1) * 16 + z * 4 + x;

        return !handler.getStackInSlot(belowIndex).isEmpty();
    }

    public static int calculateDepositIndex(Vec3 eyePos, Vec3 lookDir, BlockPos blockPos, int blockRotation) {
        Vec3 farPoint = eyePos.add(lookDir.scale(10.0));
        List<Hit> hits = new ArrayList<>();

        for (int storageIndex = 0; storageIndex < 64; storageIndex++) {
            int[] storageXYZ = xyzFromIndex(storageIndex);
            int[] visualXYZ = rotateXYZ(storageXYZ[0], storageXYZ[1], storageXYZ[2], blockRotation);

            AABB cubeBox = getCubeBox(visualXYZ[0], visualXYZ[1], visualXYZ[2], blockPos);
            Vec3 hitPos = cubeBox.clip(eyePos, farPoint).orElse(null);

            if (hitPos != null) {
                double dist = hitPos.distanceTo(eyePos);
                hits.add(new Hit(storageIndex, dist));
            }
        }

        if (hits.isEmpty()) {
            return -1;
        }

        hits.sort(Comparator.comparingDouble(h -> h.distance));
        return hits.get(hits.size() - 1).index;
    }

    private record Hit(int index, double distance) {
    }
}
