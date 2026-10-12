package com.github.crittscott.somestacks.network;

import com.github.crittscott.somestacks.server.ServerGestureState;
import com.github.crittscott.somestacks.util.StackMode;
import io.netty.handler.codec.DecoderException;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;

/** State used to interpret the next ordinary block-use packet. */
public record GestureStatePkt(StackMode mode, boolean modifierDown) {
    public static void encode(GestureStatePkt packet, FriendlyByteBuf buf) {
        buf.writeByte(packet.mode.ordinal());
        buf.writeBoolean(packet.modifierDown);
    }

    public static GestureStatePkt decode(FriendlyByteBuf buf) {
        int id = buf.readUnsignedByte();
        if (id >= StackMode.values().length) throw new DecoderException("Invalid stack mode: " + id);
        return new GestureStatePkt(StackMode.values()[id], buf.readBoolean());
    }

    public static void handleServer(GestureStatePkt packet, ServerPlayer player) {
        ServerGestureState.set(player, packet.mode, packet.modifierDown);
    }
}
