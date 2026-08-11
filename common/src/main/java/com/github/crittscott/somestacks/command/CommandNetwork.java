package com.github.crittscott.somestacks.command;

import com.github.crittscott.somestacks.network.RenderOverridePkt;
import com.github.crittscott.somestacks.network.WriteOverridesPkt;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/** Loader-specific packet delivery used by the shared command tree. */
public final class CommandNetwork {
    private CommandNetwork() {}

    public interface Handler {
        int syncAllPlayers(MinecraftServer server);

        void send(ServerPlayer player, RenderOverridePkt packet);

        void send(ServerPlayer player, WriteOverridesPkt packet);
    }

    private static Handler handler;

    public static void setHandler(Handler handler) {
        CommandNetwork.handler = handler;
    }

    public static int syncAllPlayers(MinecraftServer server) {
        return handler.syncAllPlayers(server);
    }

    public static void send(ServerPlayer player, RenderOverridePkt packet) {
        handler.send(player, packet);
    }

    public static void send(ServerPlayer player, WriteOverridesPkt packet) {
        handler.send(player, packet);
    }
}
