package com.github.crittscott.somestacks;

import com.github.crittscott.somestacks.block.StackDataMigration;
import com.github.crittscott.somestacks.command.RenderGalleryGenerator;
import com.github.crittscott.somestacks.command.SsCommand;
import com.github.crittscott.somestacks.network.ConfigSyncNetwork;
import com.github.crittscott.somestacks.network.ConfigSyncPkt;
import com.github.crittscott.somestacks.server.ServerGestureState;
import com.github.crittscott.somestacks.server.StackInteractions;
import com.mojang.brigadier.CommandDispatcher;

import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.UUID;

/** Loader-neutral server lifecycle operations called by each loader's native event adapters. */
public final class SomeStacksServer {
    private SomeStacksServer() {}

    private static ServerConfig.Settings publishedPolicy;
    private static ConfigSyncPkt clientSnapshot;

    /**
     * Starts session accounting, loads world policy, then builds the login snapshot. Loader
     * backends and networking must be installed before this server-thread callback, which runs
     * before world loading and player joins.
     */
    public static synchronized void onServerStarting(MinecraftServer server) {
        StackDataMigration.beginSession();
        ServerConfig.loadFor(server);
        ServerConfigMigration.reportRetainedIngots(server);
        publishedPolicy = ServerConfig.settings();
        clientSnapshot = ConfigSyncPkt.fromSettings(publishedPolicy);
        logPolicy("Loaded");
    }

    /** Releases the session's policy record and diagnostics after world activity ends. */
    public static synchronized void onServerStopped() {
        publishedPolicy = null;
        clientSnapshot = null;
        StackDataMigration.endSession();
    }

    /**
     * Rereads policy on the server thread and publishes it. Returns the number of players re-sent
     * client settings (0 when no client-facing value changed), or -1 when the policy is rejected.
     */
    public static int reloadPolicy(MinecraftServer server) {
        if (!ServerConfig.reload()) return -1;
        return publishPolicy(server);
    }

    /** Publishes policy changes from file reloads and persisted command edits exactly once. */
    public static int publishPolicy(MinecraftServer server) {
        ServerConfig.Settings previous = publishedPolicy;
        ServerConfig.Settings current = ServerConfig.settings();
        if (current.equals(previous)) return 0;
        publishedPolicy = current;
        if (previous.galleryEnabled() != current.galleryEnabled()
                || previous.galleryPermissionLevel() != current.galleryPermissionLevel()) {
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                server.getCommands().sendCommands(player);
            }
        }
        ConfigSyncPkt next = ConfigSyncPkt.fromSettings(current);
        int synced = 0;
        if (!next.equals(clientSnapshot)) {
            clientSnapshot = next;
            synced = ConfigSyncNetwork.syncAllPlayers(server, next);
        }
        logPolicy("Applied changed");
        return synced;
    }

    /**
     * Records the stack types, run height, gallery access and pacing, and deny-list sizes policy
     * now enforces.
     */
    private static void logPolicy(String action) {
        ServerConfig.Settings settings = ServerConfig.settings();
        SomeStacksCommon.LOGGER.info(
                "Server policy {}: Storage {}, Singles {}, Bar {}, maximum run height {}, "
                        + "galleries {} at permission {}, {} placements per tick, "
                        + "{} disabled mods, {} disabled items",
                action,
                enabledLabel(settings.enableStorageStackBlock()),
                enabledLabel(settings.enableSinglesStackBlock()),
                enabledLabel(settings.enableBarStackBlock()),
                settings.maxPileHeight(),
                enabledLabel(settings.galleryEnabled()),
                settings.galleryPermissionLevel(),
                settings.renderGalleryPlacementsPerTick(),
                settings.disableMods().size(),
                settings.disableItems().size());
    }

    private static String enabledLabel(boolean enabled) {
        return enabled ? "enabled" : "disabled";
    }

    /** Sends the startup/reload snapshot on the server thread after play networking is ready. */
    public static void onPlayerJoined(ServerPlayer player) {
        ConfigSyncNetwork.syncPlayer(player, clientSnapshot);
    }

    /** Clears gesture and sound state when this player disconnects, before a later reconnect. */
    public static void onPlayerLeft(UUID playerId) {
        ServerGestureState.clear(playerId);
        StackInteractions.clearRotationSoundPacing(playerId);
    }

    /** Advances queued gallery work once at the end of each server tick. */
    public static void onServerTickEnd() {
        RenderGalleryGenerator.onServerTick();
    }

    /** Registers server commands with the loader's dispatcher and current registry context. */
    public static void registerCommands(
            CommandDispatcher<CommandSourceStack> dispatcher, CommandBuildContext buildContext) {
        SsCommand.register(dispatcher, buildContext);
    }
}
