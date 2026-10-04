package com.github.crittscott.somestacks.network;

import com.github.crittscott.somestacks.SomeStacksCommon;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Fabric-only payload-registration marker used to require a compatible client. */
public record ProtocolPkt() implements CustomPacketPayload {
    public static final ProtocolPkt INSTANCE = new ProtocolPkt();
    public static final Type<ProtocolPkt> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(SomeStacksCommon.MODID, "protocol_3"));
    public static final StreamCodec<FriendlyByteBuf, ProtocolPkt> STREAM_CODEC =
            StreamCodec.unit(INSTANCE);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
