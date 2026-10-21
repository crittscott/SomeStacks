package com.github.crittscott.somestacks.network;

import com.github.crittscott.somestacks.PlatformServices;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/** Explicit snapshot delivery to connected players. */
public final class ConfigSyncNetwork {
    private ConfigSyncNetwork() {}

    public static void syncPlayer(ServerPlayer player, ConfigSyncPkt snapshot) {
        PlatformServices.sendConfig(player, snapshot);
    }

    /** Returns the number of players sent the payload. */
    public static int syncAllPlayers(MinecraftServer server, ConfigSyncPkt snapshot) {
        int sent = 0;
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (PlatformServices.sendConfig(player, snapshot)) sent++;
        }
        return sent;
    }
}
