package com.github.crittscott.somestacks.util;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * Builds a {@link ViewRay} from a player's eye position and block reach.
 *
 * <p>The segment runs two blocks past the reach limit: the slack vanilla allows a server-side
 * interaction (one block) plus the distance from a block's center to its farthest corner (about
 * 0.87), rounded up together. Reach is measured to the center, so a segment stopping at the limit
 * itself would leave the far cells of a block at maximum range untraceable.
 */
public final class ViewRays {
    private static final double BLOCK_CROSSING = 2.0;

    private ViewRays() {}

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
}
