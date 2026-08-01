package com.github.crittscott.somestacks.network;

import com.github.crittscott.somestacks.server.GestureThrottle;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Shared server-side validation for the mod's client-to-server mutation packets.
 * Every C2S handler runs the same boundary chain here before touching the world:
 * a real sender, one gesture per tick, a loaded target, and a target within
 * interaction range. Gesture prerequisites are exposed as individual checks the
 * handlers apply where they are relevant; world-edit protection consults live in
 * {@link com.github.crittscott.somestacks.server.Protection}.
 */
final class PacketBoundary {
    private PacketBoundary() {}

    /** Extra range beyond the attribute, matching vanilla's server-side interaction slack. */
    private static final double REACH_PADDING = 1.0;

    /**
     * Common opening chain for a positional C2S packet: a non-null sender who may act on the
     * world, a gesture left in this tick's budget, a loaded target, and a target within reach.
     * Returns the sender, or {@code null} if any check fails (the caller returns).
     *
     * <p>{@code pos} is the position the packet would change, and it is the one held to reach here.
     * A packet naming a second position names a neighbor of this one, consults it for protection,
     * and writes nothing there; its distance is bounded by the caller's own reading of what counts
     * as a gesture rather than by a reach test of its own.
     */
    static ServerPlayer validate(Supplier<NetworkEvent.Context> ctx, BlockPos pos) {
        ServerPlayer sp = ctx.get().getSender();
        if (sp == null) {
            return null;
        }
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

    static boolean withinReach(ServerPlayer sp, BlockPos pos) {
        double reach = sp.getBlockReach() + REACH_PADDING;
        return Vec3.atCenterOf(pos).distanceToSqr(sp.getEyePosition(1.0f)) <= reach * reach;
    }

    static boolean holdsInMainHand(ServerPlayer sp, Item item) {
        return sp.getMainHandItem().is(item);
    }

    static boolean mainHandEmpty(ServerPlayer sp) {
        return sp.getMainHandItem().isEmpty();
    }
}
