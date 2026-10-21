package com.github.crittscott.somestacks.network;

import com.github.crittscott.somestacks.SomeStacksCommon;
import com.github.crittscott.somestacks.server.ServerGestureState;
import com.github.crittscott.somestacks.util.StackMode;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

/** Client-to-server state needed to interpret the next vanilla block interaction. */
public record GestureStatePkt(StackMode mode, boolean modifierDown) implements CustomPacketPayload {
    public static final Type<GestureStatePkt> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(SomeStacksCommon.MODID, "gesture_state"));

    public static final StreamCodec<ByteBuf, GestureStatePkt> STREAM_CODEC = StreamCodec.composite(
            StackMode.STREAM_CODEC, GestureStatePkt::mode,
            ByteBufCodecs.BOOL, GestureStatePkt::modifierDown, GestureStatePkt::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handleServer(GestureStatePkt packet, ServerPlayer player) {
        ServerGestureState.set(player, packet.mode, packet.modifierDown);
    }
}
