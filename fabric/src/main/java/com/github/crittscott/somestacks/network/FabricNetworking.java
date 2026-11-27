package com.github.crittscott.somestacks.network;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerConfigurationNetworking;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.function.BiConsumer;

/**
 * Fabric payload registration and server receivers around the shared packet codecs and handlers.
 */
public final class FabricNetworking {
    private FabricNetworking() {}

    /**
     * Registers the configuration marker in both directions and every play payload. Runs from the
     * common initializer on the physical client and dedicated server before either side connects.
     */
    public static void registerPayloads() {
        PayloadTypeRegistry.configurationC2S().register(ProtocolPkt.TYPE, ProtocolPkt.STREAM_CODEC);
        PayloadTypeRegistry.configurationS2C().register(ProtocolPkt.TYPE, ProtocolPkt.STREAM_CODEC);
        PayloadTypeRegistry.playC2S().register(GestureStatePkt.TYPE, GestureStatePkt.STREAM_CODEC);

        PayloadTypeRegistry.playS2C().register(ConfigSyncPkt.TYPE, ConfigSyncPkt.STREAM_CODEC);
    }

    /** Builds one vanilla packet for the payload and sends that same packet to every player. */
    public static void broadcast(MinecraftServer server, CustomPacketPayload packet) {
        server.getPlayerList().broadcastAll(ServerPlayNetworking.createS2CPacket(packet));
    }

    /**
     * Registers the server receivers: the configuration-phase protocol marker, whose presence the
     * common entry point checks before play, and the gesture-state payload, handed to {@link
     * GestureStatePkt#handleServer} with its sender.
     */
    public static void initServer() {
        ServerConfigurationNetworking.registerGlobalReceiver(
                ProtocolPkt.TYPE, (payload, context) -> {});
        registerServer(GestureStatePkt.TYPE, GestureStatePkt::handleServer);
    }

    /** Sends a registered play payload to one player. */
    public static void send(ServerPlayer player, CustomPacketPayload packet) {
        ServerPlayNetworking.send(player, packet);
    }

    private static <T extends CustomPacketPayload> void registerServer(
            CustomPacketPayload.Type<T> type, BiConsumer<T, ServerPlayer> handler) {
        ServerPlayNetworking.registerGlobalReceiver(
                type, (payload, context) -> handler.accept(payload, context.player()));
    }
}
