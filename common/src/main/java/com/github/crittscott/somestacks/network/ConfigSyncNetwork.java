package com.github.crittscott.somestacks.network;

import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.Objects;
import java.util.function.BiConsumer;

/** Loader-specific delivery for the cached server configuration snapshot. */
public final class ConfigSyncNetwork {
    private ConfigSyncNetwork() {}

    private static BiConsumer<ServerPlayer, CustomPacketPayload> send;

    public static void install(BiConsumer<ServerPlayer, CustomPacketPayload> send) {
        ConfigSyncNetwork.send = Objects.requireNonNull(send);
    }

    public static void syncPlayer(ServerPlayer player) {
        for (ConfigSyncPkt packet : ConfigSyncPkt.currentPackets()) {
            send.accept(player, packet);
        }
    }

    public static int syncAllPlayers(MinecraftServer server) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            syncPlayer(player);
        }
        return server.getPlayerList().getPlayerCount();
    }
}
