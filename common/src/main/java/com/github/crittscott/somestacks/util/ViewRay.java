package com.github.crittscott.somestacks.util;

import net.minecraft.world.phys.Vec3;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.function.IntFunction;

/**
 * The segment a player's view ray sweeps against a block they are interacting with: their eye
 * position out to the far end of their block reach.
 *
 * <p>Cell targeting takes the ray as a segment rather than as a direction, so how far it reaches is
 * stated where the player is known instead of assumed inside the geometry helpers. Reach is the
 * attribute rather than a fixed distance, so a player whose reach has been raised finds the cells
 * of the block they are looking at.
 *
 * <p>{@link ViewRay#of} defines the segment's length and reach allowance.
 */
public record ViewRay(Vec3 eye, Vec3 end) {
    // Vanilla reach allowance plus the distance through the far corner of the clicked block.
    // Vanilla reach allowance plus the distance through the far corner of the clicked block.
    private static final double BLOCK_CROSSING = 2.0;

    public static ViewRay of(Player player) {
        Vec3 eye = player.getEyePosition(1.0f);
        double length = player.blockInteractionRange() + BLOCK_CROSSING;
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
        double length = player.blockInteractionRange() + BLOCK_CROSSING;
        return new ViewRay(eye, eye.add(direction.scale(length)));
    }

    /** Nearest occupied hit; equal distances retain ascending slot order. */
    public int nearestOccupied(SlotAccess slots, IntFunction<AABB> boxAt) {
        double closest = Double.MAX_VALUE;
        int selected = -1;
        for (int slot = 0; slot < slots.getSlots(); slot++) {
            if (slots.getStackInSlot(slot).isEmpty()) continue;
            Vec3 hit = boxAt.apply(slot).clip(eye, end).orElse(null);
            if (hit != null) {
                double distance = hit.distanceTo(eye);
                if (distance < closest) {
                    closest = distance;
                    selected = slot;
                }
            }
        }
        return selected;
    }

    /** Last empty hit before the first occupied hit; null slots describe a prospective block. */
    public int lastEmptyBeforeOccupied(int count, @Nullable SlotAccess slots,
                                       IntFunction<AABB> boxAt) {
        var hits = new ArrayList<Hit>();
        for (int slot = 0; slot < count; slot++) {
            Vec3 hit = boxAt.apply(slot).clip(eye, end).orElse(null);
            if (hit != null) hits.add(new Hit(slot, hit.distanceTo(eye)));
        }
        hits.sort(Comparator.comparingDouble(Hit::distance));
        int lastEmpty = -1;
        for (Hit hit : hits) {
            if (slots != null && !slots.getStackInSlot(hit.slot()).isEmpty()) return lastEmpty;
            lastEmpty = hit.slot();
        }
        return lastEmpty;
    }

    private record Hit(int slot, double distance) {}
}
