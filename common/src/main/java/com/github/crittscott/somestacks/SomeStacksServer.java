package com.github.crittscott.somestacks;

import com.github.crittscott.somestacks.block.StackBlockEntity;
import com.github.crittscott.somestacks.block.StackDataMigration;
import com.github.crittscott.somestacks.command.RenderGalleryGenerator;
import com.github.crittscott.somestacks.command.SsCommand;
import com.github.crittscott.somestacks.network.ConfigSyncNetwork;
import com.github.crittscott.somestacks.network.ConfigSyncPkt;
import com.github.crittscott.somestacks.server.ServerGestureState;
import com.github.crittscott.somestacks.server.StackInteractions;
import com.github.crittscott.somestacks.util.StackItemStorage;
import com.mojang.brigadier.CommandDispatcher;

import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Loader-neutral server lifecycle operations called by each loader's native event adapters. */
public final class SomeStacksServer {
    private SomeStacksServer() {}

    private static ServerConfig.Settings publishedPolicy;
    private static final Set<SavedItemLocation> reportedSavedItems = new HashSet<>();
    private static final Set<StackBlockEntity> pendingBlockMigrations =
            ConcurrentHashMap.newKeySet();

    private record SavedItemLocation(ResourceLocation dimension, BlockPos pos) {}

    /**
     * Starts session accounting, loads world policy, then builds the login snapshot. Loader
     * backends and networking must be installed before this server-thread callback, which runs
     * before world loading and player joins.
     */
    public static synchronized void onServerStarting(MinecraftServer server) {
        reportedSavedItems.clear();
        pendingBlockMigrations.clear();
        StackDataMigration.beginSession();
        ServerConfig.loadFor(server);
        publishedPolicy = ServerConfig.settings();
        ConfigSyncPkt.rebuildCurrent();
        ServerConfig.logPolicy("Server policy and login snapshot ready", publishedPolicy);
    }

    /** Releases the session's policy record and diagnostics after world activity ends. */
    public static synchronized void onServerStopped() {
        publishedPolicy = null;
        reportedSavedItems.clear();
        pendingBlockMigrations.clear();
        StackDataMigration.endSession();
    }

    /**
     * Reports newly retained disk entries on each occurrence, and failed persisted retries once per
     * dimension and block position per server session. Call only for server disk loads.
     */
    public static synchronized void reportSavedItems(
            ServerLevel level, BlockPos pos, StackItemStorage.LoadResult result) {
        if (result.newlySetAside() == 0 && result.failedRetries() == 0) return;
        SavedItemLocation location =
                new SavedItemLocation(level.dimension().location(), pos.immutable());
        boolean firstReport = reportedSavedItems.add(location);
        if (result.newlySetAside() > 0 || firstReport) {
            SomeStacksCommon.LOGGER.warn(
                    "Kept saved item stack(s) aside in {} at {}: {} newly retained, {} failed retries",
                    location.dimension(),
                    pos.toShortString(),
                    result.newlySetAside(),
                    result.failedRetries());
        }
    }

    /**
     * Rereads policy on the server thread and publishes it. Returns the number of players re-sent
     * client settings (0 when no client-facing value changed), or -1 when the policy is rejected.
     */
    public static int reloadPolicy(MinecraftServer server) {
        if (!ServerConfig.reload()) return -1;
        return publishPolicy(server);
    }

    /** Publishes changed policy and sends client settings only when their values change. */
    public static int publishPolicy(MinecraftServer server) {
        ServerConfig.Settings previous = publishedPolicy;
        ServerConfig.Settings current = ServerConfig.settings();
        if (current.equals(previous)) return 0;
        publishedPolicy = current;
        ServerConfig.logPolicy("Applied server policy", current);
        if (previous == null
                || previous.galleryEnabled() != current.galleryEnabled()
                || previous.galleryPermissionLevel() != current.galleryPermissionLevel()) {
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                server.getCommands().sendCommands(player);
            }
        }
        if (previous != null
                && previous.enableStorageStackBlock() == current.enableStorageStackBlock()
                && previous.enableSinglesStackBlock() == current.enableSinglesStackBlock()
                && previous.enableBarStackBlock() == current.enableBarStackBlock()
                && previous.genMods().equals(current.genMods())
                && previous.disableMods().equals(current.disableMods())) return 0;
        ConfigSyncPkt.rebuildCurrent();
        return ConfigSyncNetwork.syncAllPlayers(server);
    }

    /** Sends the startup/reload snapshot on the server thread after play networking is ready. */
    public static void onPlayerJoined(ServerPlayer player) {
        ConfigSyncNetwork.syncPlayer(player);
    }

    /** Clears gesture and sound state when this player disconnects, before a later reconnect. */
    public static void onPlayerLeft(UUID playerId) {
        ServerGestureState.clear(playerId);
        StackInteractions.clearRotationSoundPacing(playerId);
    }

    /** Defers migration publication until the block entity's chunk is attached. */
    public static void queueBlockMigration(StackBlockEntity blockEntity) {
        pendingBlockMigrations.add(blockEntity);
    }

    /** Finishes queued chunk migrations and advances gallery work at the end of a server tick. */
    public static void onServerTickEnd() {
        pendingBlockMigrations.removeIf(StackBlockEntity::finishPendingMigration);
        RenderGalleryGenerator.onServerTick();
    }

    /** Registers server commands with the loader's dispatcher and current registry context. */
    public static void registerCommands(
            CommandDispatcher<CommandSourceStack> dispatcher, CommandBuildContext buildContext) {
        SsCommand.register(dispatcher, buildContext);
    }
}
