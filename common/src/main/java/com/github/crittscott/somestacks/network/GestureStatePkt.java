package com.github.crittscott.somestacks.network;

import com.github.crittscott.somestacks.server.ServerGestureState;
import com.github.crittscott.somestacks.util.StackMode;

import io.netty.handler.codec.DecoderException;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;

/** State used to interpret the next ordinary block-use packet. */
public record GestureStatePkt(StackMode mode, boolean modifierDown) {
    public static void encode(GestureStatePkt packet, FriendlyByteBuf buf) {
        buf.writeByte(packet.mode.id());
        buf.writeBoolean(packet.modifierDown);
    }

    public static GestureStatePkt decode(FriendlyByteBuf buf) {
        int id = buf.readUnsignedByte();
        StackMode mode = StackMode.byId(id);
        if (mode == null) throw new DecoderException("Invalid stack mode: " + id);
        return new GestureStatePkt(mode, buf.readBoolean());
    }

    /**
     * Records the sender's mode and modifier on the server thread, ignoring a player who
     * disconnected while the packet was queued.
     */
    public static void handleServer(GestureStatePkt packet, ServerPlayer player) {
        if (player.hasDisconnected()) return;
        ServerGestureState.set(player, packet.mode, packet.modifierDown);
    }
}
