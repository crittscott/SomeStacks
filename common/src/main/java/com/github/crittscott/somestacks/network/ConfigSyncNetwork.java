package com.github.crittscott.somestacks.network;

import com.github.crittscott.somestacks.PlatformServices;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/** Delivery of the client policy snapshot through the platform backend. */
public final class ConfigSyncNetwork {
    private ConfigSyncNetwork() {}

    public static void syncPlayer(ServerPlayer player, ConfigSyncPkt snapshot) {
        PlatformServices.sendConfig(player, snapshot);
    }

    /** Broadcasts the snapshot once to all connected players; returns the connected player count. */
    public static int syncAllPlayers(MinecraftServer server, ConfigSyncPkt snapshot) {
        PlatformServices.broadcastConfig(server, snapshot);
        return server.getPlayerList().getPlayerCount();
    }
}
