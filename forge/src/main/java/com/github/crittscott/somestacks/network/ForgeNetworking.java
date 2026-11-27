package com.github.crittscott.somestacks.network;

import com.github.crittscott.somestacks.SomeStacksCommon;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.Channel;
import net.minecraftforge.network.ChannelBuilder;
import net.minecraftforge.network.NetworkProtocol;
import net.minecraftforge.network.SimpleChannel;

/**
 * The mod's single channel and the payload registrations on it. Client and server must agree on the
 * protocol version exactly, so both accept only their own, and the mod is required on both sides.
 *
 * <p>Registration order is positional per direction. Adding a payload anywhere but the end of its
 * {@code serverbound}/{@code clientbound} block renumbers the ones after it, which is a protocol
 * change.
 */
public final class ForgeNetworking {
    private ForgeNetworking() {}

    public static SimpleChannel CHANNEL;

    private static final StreamCodec<RegistryFriendlyByteBuf, GestureStatePkt> GESTURE_STATE_CODEC =
            StreamCodec.of(
                    GestureStatePkt.STREAM_CODEC::encode, GestureStatePkt.STREAM_CODEC::decode);

    /**
     * Builds {@link #CHANNEL} and registers the gesture-state payload to the server and the config
     * snapshot to the client. Runs once from the mod constructor, on both physical sides.
     */
    public static void init() {
        CHANNEL =
                ChannelBuilder.named(
                                ResourceLocation.fromNamespaceAndPath(
                                        SomeStacksCommon.MODID, "main"))
                        .networkProtocolVersion(SomeStacksCommon.PROTOCOL_VERSION)
                        .clientAcceptedVersions(
                                Channel.VersionTest.exact(SomeStacksCommon.PROTOCOL_VERSION))
                        .serverAcceptedVersions(
                                Channel.VersionTest.exact(SomeStacksCommon.PROTOCOL_VERSION))
                        .simpleChannel();

        CHANNEL.protocol(NetworkProtocol.PLAY)
                .serverbound()
                .addMain(
                        GestureStatePkt.class,
                        GESTURE_STATE_CODEC,
                        ForgePacketHandlers::handleGestureState)
                .clientbound()
                .addMain(
                        ConfigSyncPkt.class,
                        ConfigSyncPkt.STREAM_CODEC,
                        ForgePacketHandlers::handleClient)
                .build();
    }
}
