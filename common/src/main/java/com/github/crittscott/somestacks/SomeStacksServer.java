package com.github.crittscott.somestacks;

import com.github.crittscott.somestacks.block.StackDataMigration;
import com.github.crittscott.somestacks.command.RenderGalleryGenerator;
import com.github.crittscott.somestacks.command.SsCommand;
import com.github.crittscott.somestacks.network.ConfigSyncNetwork;
import com.github.crittscott.somestacks.network.ConfigSyncPkt;
import com.github.crittscott.somestacks.server.RotationSoundThrottle;
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

    public static void onServerStarting(MinecraftServer server) {
        StackDataMigration.beginSession();
        ServerConfig.loadFor(server);
        ServerOverridesLoader.reload();
        ConfigSyncPkt.rebuildCurrent();
    }

    public static void onServerStopped() {
        StackDataMigration.endSession();
    }

    public static void onTagsReloaded() {
        ServerConfig.rebakeIngots();
    }

    public static void onPlayerJoined(ServerPlayer player) {
        ConfigSyncNetwork.syncPlayer(player);
    }

    public static void onPlayerLeft(UUID playerId) {
        ServerGestureState.clear(playerId);
        RotationSoundThrottle.clear(playerId);
    }

    public static void onServerTickEnd() {
        RenderGalleryGenerator.onServerTick();
    }

    public static void registerCommands(
            CommandDispatcher<CommandSourceStack> dispatcher,
            CommandBuildContext buildContext) {
        SsCommand.register(dispatcher, buildContext);
    }
}
