package com.github.crittscott.somestacks.util;

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
 * <p>{@link ViewRays#of} defines the segment's length and reach allowance.
 */
public record ViewRay(Vec3 eye, Vec3 end) {
}
