package com.github.crittscott.somestacks.util;

import com.github.crittscott.somestacks.PlatformServices;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.IntFunction;
import java.util.function.IntPredicate;

import javax.annotation.Nullable;

/**
 * The segment a player's view ray sweeps against a block they are interacting with: their eye
 * position out to the far end of their block reach, and the two walks that pick a cell inside a
 * stack along it.
 *
 * <p>Cell targeting takes the ray as a segment rather than as a direction, so how far it reaches is
 * stated where the player is known instead of assumed inside the geometry helpers. Reach is the
 * loader's answer for this player ({@link PlatformServices#blockReach}) rather than a fixed
 * distance, so where the loader lets reach be raised, the player still finds the cells of the block
 * they are looking at.
 *
 * <p>The segment runs two blocks past the reach limit: the slack vanilla allows a server-side
 * interaction (one block) plus the distance from a block's center to its farthest corner (about
 * 0.87), rounded up together. Reach is measured to the center, so a segment stopping at the limit
 * itself would leave the far cells of a block at maximum range untraceable.
 */
public record ViewRay(Vec3 eye, Vec3 end) {
    private static final double BLOCK_CROSSING = 2.0;

    public static ViewRay of(Player player) {
        Vec3 eye = player.getEyePosition(1.0f);
        double length = PlatformServices.blockReach(player) + BLOCK_CROSSING;
        return new ViewRay(eye, eye.add(player.getLookAngle().scale(length)));
    }

    /** Builds a reach-length ray through the exact point carried by a vanilla block interaction. */
    public static ViewRay through(Player player, Vec3 hitLocation) {
        Vec3 eye = player.getEyePosition(1.0f);
        Vec3 direction = hitLocation.subtract(eye);
        if (direction.lengthSqr() < 1.0E-8) {
            direction = player.getLookAngle();
        } else {
            direction = direction.normalize();
        }
        double length = PlatformServices.blockReach(player) + BLOCK_CROSSING;
        return new ViewRay(eye, eye.add(direction.scale(length)));
    }

    /**
     * The cell a deposit targets in the block at {@code pos}: the last empty cell along the ray
     * before the first occupied one, or the farthest crossed cell when the ray meets none occupied;
     * -1 when it crosses no cell or the first cell it crosses is occupied. Equally distant cells
     * keep index order.
     *
     * @param cells the block's cell count
     * @param localBox each cell's block-local box at the block's layout
     * @param occupied which cells hold something, or null for a prospective, empty block
     */
    public int depositTarget(
            BlockPos pos, int cells, IntFunction<AABB> localBox, @Nullable IntPredicate occupied) {
        List<Hit> hits = new ArrayList<>();
        for (int index = 0; index < cells; index++) {
            Vec3 hit = crossing(pos, localBox.apply(index));
            if (hit != null) {
                hits.add(new Hit(index, hit.distanceTo(eye)));
            }
        }
        hits.sort(Comparator.comparingDouble(Hit::distance));

        int lastEmpty = -1;
        for (Hit hit : hits) {
            if (occupied != null && occupied.test(hit.index)) {
                return lastEmpty;
            }
            lastEmpty = hit.index;
        }
        return lastEmpty;
    }

    /**
     * The nearest occupied cell the ray crosses in the block at {@code pos}, or -1 on a miss. Of
     * equally distant cells, the lowest index wins.
     *
     * @param cells the block's cell count
     * @param localBox each cell's block-local box at the block's layout
     * @param occupied which cells hold something
     */
    public int nearestOccupied(
            BlockPos pos, int cells, IntFunction<AABB> localBox, IntPredicate occupied) {
        double closestDistance = Double.MAX_VALUE;
        int closestIndex = -1;
        for (int index = 0; index < cells; index++) {
            if (!occupied.test(index)) continue;
            Vec3 hit = crossing(pos, localBox.apply(index));
            if (hit != null) {
                double distance = hit.distanceTo(eye);
                if (distance < closestDistance) {
                    closestDistance = distance;
                    closestIndex = index;
                }
            }
        }
        return closestIndex;
    }

    /** Where the ray enters a block-local box placed at {@code pos}, or null when it misses. */
    @Nullable
    private Vec3 crossing(BlockPos pos, AABB localBox) {
        return localBox.move(pos.getX(), pos.getY(), pos.getZ()).clip(eye, end).orElse(null);
    }

    private record Hit(int index, double distance) {}
}
