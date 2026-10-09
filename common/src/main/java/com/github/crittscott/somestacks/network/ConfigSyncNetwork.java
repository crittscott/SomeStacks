package com.github.crittscott.somestacks.network;

import com.github.crittscott.somestacks.PlatformServices;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/** Loader-specific delivery for the cached server configuration snapshot. */
public final class ConfigSyncNetwork {
    private ConfigSyncNetwork() {}

    public static void syncPlayer(ServerPlayer player) {
        for (ConfigSyncPkt packet : ConfigSyncPkt.currentPackets()) {
            PlatformServices.sendConfig(player, packet);
        }
    }

    public static int syncAllPlayers(MinecraftServer server) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            syncPlayer(player);
        }
        return server.getPlayerList().getPlayerCount();
    }
}
