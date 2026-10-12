package com.github.crittscott.somestacks;

import com.github.crittscott.somestacks.command.RenderGalleryGenerator;
import com.github.crittscott.somestacks.network.ConfigSyncPkt;
import com.github.crittscott.somestacks.server.GestureThrottle;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.storage.LevelResource;

import java.util.UUID;

/** Shared server-session policy, login synchronization, and cleanup. */
public final class ServerLifecycle {
    private ServerLifecycle() {}

    public static void starting(MinecraftServer server) {
        RenderGalleryGenerator.clear();
        GestureThrottle.clearAll();
        ServerConfig.load(server.getWorldPath(LevelResource.ROOT)
                .resolve("serverconfig").resolve(SomeStacksCommon.MODID + "-server.json"));
    }

    public static void stopped() {
        RenderGalleryGenerator.clear();
        GestureThrottle.clearAll();
        ServerConfig.clear();
    }

    public static void login(ServerPlayer player) {
        PlatformServices.sendConfig(player, configSync());
    }

    public static void logout(UUID playerId) {
        GestureThrottle.clear(playerId);
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
