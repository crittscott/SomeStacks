package com.github.crittscott.somestacks;

import com.github.crittscott.somestacks.command.RenderGalleryGenerator;
import com.github.crittscott.somestacks.network.ConfigSyncPkt;
import com.github.crittscott.somestacks.server.GestureThrottle;
import com.github.crittscott.somestacks.server.ServerGestureState;
import com.github.crittscott.somestacks.util.StackItemStorage;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/** Shared server-session policy, login synchronization, and cleanup. */
public final class ServerLifecycle {
    private static final Set<SavedItemLocation> reportedSavedItems = new HashSet<>();

    private record SavedItemLocation(ResourceLocation dimension, BlockPos pos) {}

    private ServerLifecycle() {}

    /**
     * Clears queued galleries, gesture pacing, synchronized gesture state, and set-aside reports for
     * a new server session. The loader must separately initialize its world policy before players
     * log in.
     */
    public static synchronized void starting() {
        RenderGalleryGenerator.clear();
        GestureThrottle.clearAll();
        ServerGestureState.clearAll();
        reportedSavedItems.clear();
    }

    /**
     * Clears galleries, gesture state, and set-aside reports and releases world policy, its saver,
     * and derived admission sets after server shutdown. Loader-owned cleanup remains the loader's
     * responsibility.
     */
    public static synchronized void stopped() {
        RenderGalleryGenerator.clear();
        GestureThrottle.clearAll();
        ServerGestureState.clearAll();
        reportedSavedItems.clear();
        ServerConfig.clear();
    }

    /**
     * Logs saved item stacks a block kept aside because they could not be read. A location is
     * reported the first time in a session, and again whenever a load sets new entries aside.
     */
    public static synchronized void reportSavedItems(
            ServerLevel level, BlockPos pos, StackItemStorage.LoadResult result) {
        if (result.newlySetAside() == 0 && result.failedRetries() == 0) {
            return;
        }
        SavedItemLocation location = new SavedItemLocation(level.dimension().location(), pos.immutable());
        boolean firstReport = reportedSavedItems.add(location);
        if (result.newlySetAside() > 0 || firstReport) {
            SomeStacksCommon.LOGGER.warn(
                    "Kept saved item stack(s) aside in {} at {}: {} newly retained, {} failed retries",
                    location.dimension(), pos.toShortString(), result.newlySetAside(), result.failedRetries());
        }
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
