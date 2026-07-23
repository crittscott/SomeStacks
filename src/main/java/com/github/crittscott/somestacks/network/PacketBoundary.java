package com.github.crittscott.somestacks.network;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Shared server-side validation for the mod's client-to-server mutation packets.
 * Every C2S handler runs the same boundary chain here before touching the world:
 * a real sender, a loaded target, and a target within interaction range. Gesture
 * prerequisites are exposed as individual checks the handlers apply where they are
 * relevant; world-edit protection consults live in
 * {@link com.github.crittscott.somestacks.server.Protection}.
 */
final class PacketBoundary {
    private PacketBoundary() {}

    /** Extra range beyond the attribute, matching vanilla's server-side interaction slack. */
    private static final double REACH_PADDING = 1.0;

    /**
     * Common opening chain for a positional C2S packet: non-null sender, loaded target,
     * and within reach. Returns the sender, or {@code null} if any check fails (the caller returns).
     */
    static ServerPlayer validate(Supplier<NetworkEvent.Context> ctx, BlockPos pos) {
        ServerPlayer sp = ctx.get().getSender();
        if (sp == null) {
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
