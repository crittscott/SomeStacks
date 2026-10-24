package com.github.crittscott.somestacks.network;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerConfigurationNetworking;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;

import java.util.function.BiConsumer;

/** Fabric payload registration and server receivers around the shared packet codecs and handlers. */
public final class FabricNetworking {
    private FabricNetworking() {}

    /**
     * Registers the configuration marker in both directions and every play payload. Runs from the
     * common initializer on the physical client and dedicated server before either side connects.
     */
    public static void registerPayloads() {
        PayloadTypeRegistry.configurationC2S().register(
                ProtocolPkt.TYPE, ProtocolPkt.STREAM_CODEC);
        PayloadTypeRegistry.configurationS2C().register(
                ProtocolPkt.TYPE, ProtocolPkt.STREAM_CODEC);
        PayloadTypeRegistry.playC2S().register(GestureStatePkt.TYPE, GestureStatePkt.STREAM_CODEC);

        PayloadTypeRegistry.playS2C().register(ConfigSyncPkt.TYPE, ConfigSyncPkt.STREAM_CODEC);
    }

    public static void initServer() {
        ServerConfigurationNetworking.registerGlobalReceiver(
                ProtocolPkt.TYPE, (payload, context) -> { });
        registerServer(GestureStatePkt.TYPE, GestureStatePkt::handleServer);
    }

    public static void send(ServerPlayer player, CustomPacketPayload packet) {
        ServerPlayNetworking.send(player, packet);
    }

    private static <T extends CustomPacketPayload> void registerServer(
            CustomPacketPayload.Type<T> type, BiConsumer<T, ServerPlayer> handler) {
        ServerPlayNetworking.registerGlobalReceiver(type,
                (payload, context) -> handler.accept(payload, context.player()));
    }
}
