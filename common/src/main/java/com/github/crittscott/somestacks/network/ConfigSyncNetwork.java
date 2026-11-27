package com.github.crittscott.somestacks.network;

import com.github.crittscott.somestacks.PlatformServices;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.function.Consumer;

/** Delivery of the cached server configuration snapshot through the platform backend. */
public final class ConfigSyncNetwork {
    private ConfigSyncNetwork() {}

    /** Sends the cached sequence after backend installation and startup/reload snapshot rebuild. */
    public static void syncPlayer(ServerPlayer player) {
        sendCurrent(packet -> PlatformServices.sendConfig(player, packet));
    }

    /** Delivers every cached chunk in order on the caller's thread, without rebuilding policy. */
    public static void sendCurrent(Consumer<ConfigSyncPkt> sender) {
        for (ConfigSyncPkt packet : ConfigSyncPkt.currentPackets()) {
            sender.accept(packet);
        }
    }

    /**
     * Broadcasts each chunk of the rebuilt snapshot once to all connected players; returns the
     * connected player count.
     */
    public static int syncAllPlayers(MinecraftServer server) {
        sendCurrent(packet -> PlatformServices.broadcastConfig(server, packet));
        return server.getPlayerList().getPlayerCount();
    }
}
