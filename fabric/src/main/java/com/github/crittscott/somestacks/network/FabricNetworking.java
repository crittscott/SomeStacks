package com.github.crittscott.somestacks.network;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.function.BiConsumer;

/** Fabric payload registration and server receivers around the shared packet codecs and handlers. */
public final class FabricNetworking {
    private FabricNetworking() {}

    /**
     * Registers every payload type on both play directions. Runs from the common initializer so it
     * happens on the physical client and the dedicated server alike, before either side joins.
     */
    public static void registerPayloads() {
        PayloadTypeRegistry.playC2S().register(GestureStatePkt.TYPE, GestureStatePkt.STREAM_CODEC);

        PayloadTypeRegistry.playS2C().register(ProtocolPkt.TYPE, ProtocolPkt.STREAM_CODEC);
        PayloadTypeRegistry.playS2C().register(ConfigSyncPkt.TYPE, ConfigSyncPkt.STREAM_CODEC);
        PayloadTypeRegistry.playS2C().register(RenderOverridePkt.TYPE, RenderOverridePkt.STREAM_CODEC);
        PayloadTypeRegistry.playS2C().register(WriteOverridesPkt.TYPE, WriteOverridesPkt.STREAM_CODEC);
    }

    public static void initServer() {
        registerServer(GestureStatePkt.TYPE, GestureStatePkt::handleServer);
    }

    public static boolean supportsClient(ServerPlayer player) {
        return ServerPlayNetworking.canSend(player, ProtocolPkt.TYPE);
    }

    public static void sendConfig(ServerPlayer player) {
        ServerPlayNetworking.send(player, ConfigSyncPkt.current());
    }

    public static int syncAllPlayers(MinecraftServer server) {
        ConfigSyncPkt packet = ConfigSyncPkt.current();
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            ServerPlayNetworking.send(player, packet);
        }
        return server.getPlayerList().getPlayerCount();
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
