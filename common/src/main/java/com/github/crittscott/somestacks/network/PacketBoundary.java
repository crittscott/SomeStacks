package com.github.crittscott.somestacks.network;

import com.github.crittscott.somestacks.server.GestureThrottle;
import com.github.crittscott.somestacks.util.PlayerReach;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.phys.Vec3;

/**
 * Shared server-side validation for the mod's client-to-server mutation packets.
 * Every C2S handler runs the same boundary chain here before touching the world:
 * a real sender, one gesture per tick, nonspectator status, a loaded target, and a
 * target within interaction range. Gesture prerequisites are exposed as individual
 * checks the handlers apply where they are relevant; world-edit protection consults
 * live behind {@link com.github.crittscott.somestacks.server.PlayerEdits}.
 */
public final class PacketBoundary {
    private PacketBoundary() {}

    /** Extra range beyond the attribute, matching vanilla's server-side interaction slack. */
    private static final double REACH_PADDING = 1.0;

    /**
     * Common opening chain for a positional C2S packet: a non-null sender, a gesture left in this
     * tick's budget, a sender who is not a spectator, a loaded target, and a target within reach.
     * Returns the sender, or {@code null} if any check fails (the caller returns).
     *
     * <p>{@code pos} is the position the packet would change, and it is the one held to reach here.
     * A packet naming a second position names a neighbor of this one, consults it for protection,
     * and writes nothing there; its distance is bounded by the caller's own reading of what counts
     * as a gesture rather than by a reach test of its own.
     */
    static ServerPlayer validate(ServerPlayer sp, BlockPos pos) {
        if (!GestureThrottle.claimTick(sp)) {
            return null;
        }
        // A spectator passes through the world without touching it. Vanilla stops the interaction
        // that would reach a block at the game mode; the gestures these packets carry take the
        // place of that interaction, so they enforce the same restriction here.
        if (sp.isSpectator()) {
            return null;
        }
        if (!sp.serverLevel().isLoaded(pos)) {
            return null;
        }
        if (!withinReach(sp, pos)) {
            return null;
        }
        return sp;
    }

    public static boolean withinReach(ServerPlayer sp, BlockPos pos) {
        double reach = PlayerReach.blockReach(sp) + REACH_PADDING;
        return Vec3.atCenterOf(pos).distanceToSqr(sp.getEyePosition(1.0f)) <= reach * reach;
    }

    public static boolean holdsInMainHand(ServerPlayer sp, Item item) {
        return sp.getMainHandItem().is(item);
    }

    public static boolean mainHandEmpty(ServerPlayer sp) {
        return sp.getMainHandItem().isEmpty();
    }
}
