package com.github.crittscott.somestacks.network;

import com.github.crittscott.somestacks.PlatformServices;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import java.util.function.Consumer;

/** Loader-specific delivery for the cached server configuration snapshot. */
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

    /** Sends the rebuilt snapshot to all connected players; returns the connected player count. */
    public static int syncAllPlayers(MinecraftServer server) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            syncPlayer(player);
        }
        return server.getPlayerList().getPlayerCount();
    }
}
