package com.github.crittscott.somestacks.command;

import com.github.crittscott.somestacks.network.ConfigSyncPkt;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.Objects;
import java.util.function.BiConsumer;

/** Loader-specific packet delivery used by the shared command tree. */
public final class CommandNetwork {
    private CommandNetwork() {}

    private static BiConsumer<ServerPlayer, CustomPacketPayload> send;

    public static void install(BiConsumer<ServerPlayer, CustomPacketPayload> send) {
        CommandNetwork.send = Objects.requireNonNull(send);
    }

    public static int syncAllPlayers(MinecraftServer server) {
        ConfigSyncPkt packet = ConfigSyncPkt.current();
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            send(player, packet);
        }
        return server.getPlayerList().getPlayerCount();
    }

    public static void send(ServerPlayer player, CustomPacketPayload packet) {
        send.accept(player, packet);
    }
}
