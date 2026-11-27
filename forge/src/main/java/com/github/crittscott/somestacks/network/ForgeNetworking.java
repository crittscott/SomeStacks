package com.github.crittscott.somestacks.network;

import com.github.crittscott.somestacks.SomeStacksCommon;

import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;

import java.util.Optional;

/**
 * The mod's single channel and the packet registrations on it. Client and server must agree on the
 * protocol version exactly, so both accept only their own, and the mod is required on both sides.
 */
public final class ForgeNetworking {
    private ForgeNetworking() {}

    private static final int CONFIG_SYNC_ID = 1;
    private static final int GESTURE_STATE_ID = 0;
    private static final String PROTOCOL = String.valueOf(SomeStacksCommon.PROTOCOL_VERSION);
    public static SimpleChannel CHANNEL;

    /**
     * Creates the channel and registers both messages; runs once from the mod constructor, before
     * any connection exists, and must precede every {@link #CHANNEL} use.
     */
    public static void init() {
        CHANNEL =
                NetworkRegistry.ChannelBuilder.named(
                                new ResourceLocation(SomeStacksCommon.MODID, "main"))
                        .networkProtocolVersion(() -> PROTOCOL)
                        .clientAcceptedVersions(PROTOCOL::equals)
                        .serverAcceptedVersions(PROTOCOL::equals)
                        .simpleChannel();

        Optional<NetworkDirection> toServer = Optional.of(NetworkDirection.PLAY_TO_SERVER);
        Optional<NetworkDirection> toClient = Optional.of(NetworkDirection.PLAY_TO_CLIENT);

        CHANNEL.registerMessage(
                GESTURE_STATE_ID,
                GestureStatePkt.class,
                GestureStatePkt::encode,
                GestureStatePkt::decode,
                ForgePacketHandlers::handleGestureState,
                toServer);
        CHANNEL.registerMessage(
                CONFIG_SYNC_ID,
                ConfigSyncPkt.class,
                ConfigSyncPkt::encode,
                ConfigSyncPkt::decode,
                ForgePacketHandlers::handleConfigSync,
                toClient);
    }
}
