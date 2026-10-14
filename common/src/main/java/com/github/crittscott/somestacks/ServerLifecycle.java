package com.github.crittscott.somestacks;

import com.github.crittscott.somestacks.command.RenderGalleryGenerator;
import com.github.crittscott.somestacks.network.ConfigSyncPkt;
import com.github.crittscott.somestacks.server.GestureThrottle;
import com.github.crittscott.somestacks.server.ServerGestureState;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.UUID;

/** Shared server-session policy, login synchronization, and cleanup. */
public final class ServerLifecycle {
    private ServerLifecycle() {}

    /**
     * Clears queued galleries, gesture pacing, and synchronized gesture state for a new server
     * session. The loader must separately initialize its world policy before players log in.
     */
    public static void starting() {
        RenderGalleryGenerator.clear();
        GestureThrottle.clearAll();
        ServerGestureState.clearAll();
    }

    /**
     * Clears galleries and gesture state and releases world policy, its saver, and derived
     * admission sets after server shutdown. Loader-owned cleanup remains the loader's responsibility.
     */
    public static void stopped() {
        RenderGalleryGenerator.clear();
        GestureThrottle.clearAll();
        ServerGestureState.clearAll();
        ServerConfig.clear();
    }

    /**
     * Sends the current enable flags and freshly read server render overrides to this player.
     * World policy must already be initialized; this does not reread policy files.
     */
    public static void login(ServerPlayer player) {
        PlatformServices.sendConfig(player, configSync());
    }

    /**
     * Removes this player's gesture pacing and synchronized mode/modifier state.
     */
    public static void logout(UUID playerId) {
        GestureThrottle.clear(playerId);
        ServerGestureState.clear(playerId);
    }

    /** Reads render overrides once per broadcast; policy files are not reread. */
    public static int syncAllPlayers(MinecraftServer server) {
        ConfigSyncPkt packet = configSync();
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            PlatformServices.sendConfig(player, packet);
        }
        return server.getPlayerList().getPlayerCount();
    }

    private static ConfigSyncPkt configSync() {
        ServerConfig.Settings settings = ServerConfig.settings();
        return new ConfigSyncPkt(settings.enableStorageStackBlock(), settings.enableSinglesStackBlock(),
                settings.enableBarStackBlock(), ServerOverridesLoader.load());
    }
}
