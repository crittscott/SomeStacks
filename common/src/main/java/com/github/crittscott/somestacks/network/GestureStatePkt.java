package com.github.crittscott.somestacks.network;

import com.github.crittscott.somestacks.SomeStacksCommon;
import com.github.crittscott.somestacks.server.ServerGestureState;
import com.github.crittscott.somestacks.util.StackMode;

import io.netty.buffer.ByteBuf;
import io.netty.handler.codec.DecoderException;

import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

/** Client-to-server state needed to interpret the next vanilla block interaction. */
public record GestureStatePkt(StackMode mode, boolean modifierDown) implements CustomPacketPayload {
    public static final Type<GestureStatePkt> TYPE =
            new Type<>(
                    ResourceLocation.fromNamespaceAndPath(SomeStacksCommon.MODID, "gesture_state"));

    public static final StreamCodec<ByteBuf, GestureStatePkt> STREAM_CODEC =
            StreamCodec.of(
                    (buf, packet) -> {
                        buf.writeByte(packet.mode.id());
                        buf.writeBoolean(packet.modifierDown);
                    },
                    buf -> {
                        int id = buf.readUnsignedByte();
                        StackMode mode = StackMode.byId(id);
                        if (mode == null) {
                            throw new DecoderException("Invalid stack mode: " + id);
                        }
                        return new GestureStatePkt(mode, buf.readBoolean());
                    });

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    /** Records the sender's reported state; loaders call this on the server thread. */
    public static void handleServer(GestureStatePkt packet, ServerPlayer player) {
        ServerGestureState.set(player, packet.mode, packet.modifierDown);
    }
}
