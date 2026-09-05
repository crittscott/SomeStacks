package com.github.crittscott.somestacks.network;

import com.github.crittscott.somestacks.ServerConfig;
import com.github.crittscott.somestacks.ServerOverridesLoader;
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
        PayloadTypeRegistry.playC2S().register(PlaceAndDepositPkt.TYPE, PlaceAndDepositPkt.STREAM_CODEC);
        PayloadTypeRegistry.playC2S().register(DepositPkt.TYPE, DepositPkt.STREAM_CODEC);
        PayloadTypeRegistry.playC2S().register(TogglePermanentPkt.TYPE, TogglePermanentPkt.STREAM_CODEC);
        PayloadTypeRegistry.playC2S().register(RotateBlockPkt.TYPE, RotateBlockPkt.STREAM_CODEC);
        PayloadTypeRegistry.playC2S().register(RotateItemPkt.TYPE, RotateItemPkt.STREAM_CODEC);
        PayloadTypeRegistry.playC2S().register(ExtractPkt.TYPE, ExtractPkt.STREAM_CODEC);

        PayloadTypeRegistry.playS2C().register(ProtocolPkt.TYPE, ProtocolPkt.STREAM_CODEC);
        PayloadTypeRegistry.playS2C().register(ConfigSyncPkt.TYPE, ConfigSyncPkt.STREAM_CODEC);
        PayloadTypeRegistry.playS2C().register(RenderOverridePkt.TYPE, RenderOverridePkt.STREAM_CODEC);
        PayloadTypeRegistry.playS2C().register(WriteOverridesPkt.TYPE, WriteOverridesPkt.STREAM_CODEC);
    }

    public static void initServer() {
        registerServer(PlaceAndDepositPkt.TYPE, PlaceAndDepositPkt::handleServer);
        registerServer(DepositPkt.TYPE, DepositPkt::handleServer);
        registerServer(TogglePermanentPkt.TYPE, TogglePermanentPkt::handleServer);
        registerServer(RotateBlockPkt.TYPE, RotateBlockPkt::handleServer);
        registerServer(RotateItemPkt.TYPE, RotateItemPkt::handleServer);
        registerServer(ExtractPkt.TYPE, ExtractPkt::handleServer);
    }

    public static boolean supportsClient(ServerPlayer player) {
        return ServerPlayNetworking.canSend(player, ProtocolPkt.TYPE);
    }

    public static void sendProtocol(ServerPlayer player) {
        ServerPlayNetworking.send(player, ProtocolPkt.INSTANCE);
    }

    public static void sendConfig(ServerPlayer player) {
        ServerPlayNetworking.send(player, buildConfigSync());
    }

    public static int syncAllPlayers(MinecraftServer server) {
        ConfigSyncPkt packet = buildConfigSync();
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            ServerPlayNetworking.send(player, packet);
        }
        return server.getPlayerList().getPlayerCount();
    }

    public static void sendRenderOverride(ServerPlayer player, RenderOverridePkt packet) {
        ServerPlayNetworking.send(player, packet);
    }

    public static void sendWriteOverrides(ServerPlayer player, WriteOverridesPkt packet) {
        ServerPlayNetworking.send(player, packet);
    }

    private static ConfigSyncPkt buildConfigSync() {
        return new ConfigSyncPkt(
                ServerConfig.enableStorageStackBlock(),
                ServerConfig.enableSinglesStackBlock(),
                ServerConfig.enableBarStackBlock(),
                ServerOverridesLoader.load());
    }

    private static <T extends CustomPacketPayload> void registerServer(
            CustomPacketPayload.Type<T> type, BiConsumer<T, ServerPlayer> handler) {
        ServerPlayNetworking.registerGlobalReceiver(type, (payload, context) -> {
            ServerPlayer player = context.player();
            player.server.execute(() -> handler.accept(payload, player));
        });
    }
}
