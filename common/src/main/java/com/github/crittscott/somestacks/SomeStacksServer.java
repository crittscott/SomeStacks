package com.github.crittscott.somestacks;

import com.github.crittscott.somestacks.block.StackDataMigration;
import com.github.crittscott.somestacks.command.RenderGalleryGenerator;
import com.github.crittscott.somestacks.command.SsCommand;
import com.github.crittscott.somestacks.network.ConfigSyncNetwork;
import com.github.crittscott.somestacks.network.ConfigSyncPkt;
import com.github.crittscott.somestacks.server.ServerGestureState;
import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.UUID;

/** Loader-neutral server lifecycle operations called by each loader's native event adapters. */
public final class SomeStacksServer {
    private SomeStacksServer() {}

    private static volatile MinecraftServer runningServer;
    private static ServerConfig.Settings publishedPolicy;
    private static ConfigSyncPkt clientSnapshot;

    /**
     * Starts session accounting, loads world policy, then builds the login snapshot. Loader
     * backends and networking must be installed before this server-thread callback, which runs
     * before world loading and player joins.
     */
    public static synchronized void onServerStarting(MinecraftServer server) {
        runningServer = server;
        StackDataMigration.beginSession();
        if (!(ServerConfig.backend() instanceof TomlServerConfig)) ServerConfigMigration.reportLegacyIngots(server);
        ServerConfig.loadFor(server);
        publishedPolicy = ServerConfig.settings();
        clientSnapshot = ConfigSyncPkt.fromSettings(publishedPolicy);
        logPolicy("Loaded");
    }

    /** Records the enabled stack types, run height limit, gallery access, and deny-list sizes. */
    private static void logPolicy(String action) {
        ServerConfig.Settings policy = ServerConfig.settings();
        SomeStacksCommon.LOGGER.info(
                "{} server policy: Storage {}, Singles {}, Bar {}; maximum run height {}; galleries {} at permission {}; {} disabled mods, {} disabled items",
                action, enabledLabel(policy.enableStorageStackBlock()), enabledLabel(policy.enableSinglesStackBlock()),
                enabledLabel(policy.enableBarStackBlock()), policy.maxPileHeight(),
                enabledLabel(policy.galleryEnabled()), policy.galleryPermissionLevel(),
                policy.disableMods().size(), policy.disableItems().size());
    }

    private static String enabledLabel(boolean enabled) {
        return enabled ? "enabled" : "disabled";
    }

    /** Releases the stopped server and clears session diagnostics after world activity ends. */
    public static synchronized void onServerStopped() {
        runningServer = null;
        publishedPolicy = null;
        clientSnapshot = null;
        StackDataMigration.endSession();
    }

    /**
     * Queues a native policy reload on the running server thread; inactive sessions are ignored.
     * Changed policy rebuilds the cached snapshot before synchronizing connected players.
     */
    public static void onPolicyReloaded() {
        MinecraftServer server = runningServer;
        if (server != null) {
            server.execute(() -> reloadPolicy(server));
        }
    }

    /** Refreshes policy on the server thread and synchronizes only changed policy. */
    public static int reloadPolicy(MinecraftServer server) {
        ServerConfig.reload();
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
        logPolicy("Reloaded");
        return synced;
    }

    /** Sends the startup/reload snapshot on the server thread after play networking is ready. */
    public static void onPlayerJoined(ServerPlayer player) {
        ConfigSyncNetwork.syncPlayer(player, clientSnapshot);
    }

    /** Clears gesture and sound state when this player disconnects, before a later reconnect. */
    public static void onPlayerLeft(UUID playerId) {
        ServerGestureState.clear(playerId);
    }

    /** Advances queued gallery work once at the end of each server tick. */
    public static void onServerTickEnd() {
        RenderGalleryGenerator.onServerTick();
    }

    /** Registers server commands with the loader's dispatcher and current registry context. */
    public static void registerCommands(
            CommandDispatcher<CommandSourceStack> dispatcher,
            CommandBuildContext buildContext) {
        SsCommand.register(dispatcher, buildContext);
    }
}
