package com.github.crittscott.somestacks.network;

import com.github.crittscott.somestacks.SomeStacksCommon;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Empty server-to-client marker sent on login. Its presence in the connection's payload registry is
 * how each side confirms the other runs a compatible build; carrying a new id whenever the wire
 * format changes rejects a mismatched peer at the handshake.
 */
public record ProtocolPkt() implements CustomPacketPayload {
    public static final ProtocolPkt INSTANCE = new ProtocolPkt();

    public static final Type<ProtocolPkt> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(SomeStacksCommon.MODID, "protocol_2"));
    public static final StreamCodec<FriendlyByteBuf, ProtocolPkt> STREAM_CODEC =
            StreamCodec.unit(INSTANCE);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
