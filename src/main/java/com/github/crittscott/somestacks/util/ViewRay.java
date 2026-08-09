package com.github.crittscott.somestacks.util;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * The segment a player's view ray sweeps against a block they are interacting with: their eye
 * position out to the far end of their block reach.
 *
 * <p>Cell targeting takes the ray as a segment rather than as a direction, so how far it reaches is
 * stated where the player is known instead of assumed inside the geometry helpers. Reach is the
 * attribute rather than a fixed distance, so a player whose reach has been raised finds the cells
 * of the block they are looking at.
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
        double length = player.getBlockReach() + BLOCK_CROSSING;
        return new ViewRay(eye, eye.add(player.getLookAngle().scale(length)));
    }
}
