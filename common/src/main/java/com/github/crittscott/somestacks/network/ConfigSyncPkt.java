package com.github.crittscott.somestacks.network;

import com.github.crittscott.somestacks.SomeStacksCommon;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Server stack-type flags, sent at login and when their values change. */
public record ConfigSyncPkt(boolean enableStack, boolean enableSingles, boolean enableBar)
        implements CustomPacketPayload {
    public static final Type<ConfigSyncPkt> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(SomeStacksCommon.MODID, "config_sync"));
    public static final StreamCodec<FriendlyByteBuf, ConfigSyncPkt> STREAM_CODEC =
            StreamCodec.ofMember(ConfigSyncPkt::encode, ConfigSyncPkt::decode);

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void encode(ConfigSyncPkt packet, FriendlyByteBuf buffer) {
        buffer.writeBoolean(packet.enableStack());
        buffer.writeBoolean(packet.enableSingles());
        buffer.writeBoolean(packet.enableBar());
    }

    public static ConfigSyncPkt decode(FriendlyByteBuf buffer) {
        return new ConfigSyncPkt(buffer.readBoolean(), buffer.readBoolean(), buffer.readBoolean());
    }
}
