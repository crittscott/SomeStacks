package com.github.crittscott.somestacks.network;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.util.BlockSnapshot;
import net.minecraftforge.event.ForgeEventFactory;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Shared server-side validation for the mod's client-to-server mutation packets.
 * Every C2S handler runs the same boundary chain here before touching the world:
 * a real sender, a loaded target, and a target within interaction range. Gesture
 * prerequisites and protection consults are exposed as individual checks the handlers
 * apply where they are relevant.
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

    /**
     * Server-authoritative protection consult for a world edit at {@code pos}:
     * world border and vanilla spawn protection (which already exempts operators and other
     * dimensions). {@code true} means the edit must not proceed.
     */
    static boolean isProtected(ServerPlayer sp, BlockPos pos) {
        ServerLevel level = sp.serverLevel();
        if (!level.getWorldBorder().isWithinBounds(pos)) {
            return true;
        }
        MinecraftServer server = level.getServer();
        return server.isUnderSpawnProtection(level, pos, sp);
    }

    /**
     * Places {@code state} at {@code pos} and fires {@link net.minecraftforge.event.level.BlockEvent.EntityPlaceEvent}
     * so claim/protection/logging mods can veto or record it. Restores the previous state and
     * returns {@code false} on a failed set or a vetoed event; {@code true} when the block stands.
     */
    static boolean placeBlockChecked(ServerPlayer sp, BlockPos pos, BlockState state, Direction face) {
        ServerLevel level = sp.serverLevel();
        BlockSnapshot snapshot = BlockSnapshot.create(level.dimension(), level, pos);
        if (!level.setBlock(pos, state, 3)) {
            return false;
        }
        if (ForgeEventFactory.onBlockPlace(sp, snapshot, face.getOpposite())) {
            snapshot.restore(true, false);
            return false;
        }
        return true;
    }
}
