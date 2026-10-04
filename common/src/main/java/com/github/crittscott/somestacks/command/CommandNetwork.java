package com.github.crittscott.somestacks.command;

import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.function.ToIntFunction;

/** Loader-specific packet delivery used by the shared command tree. */
public final class CommandNetwork {
    private CommandNetwork() {}

    private static ToIntFunction<MinecraftServer> syncAllPlayers;
    private static BiConsumer<ServerPlayer, CustomPacketPayload> send;

    public static void install(
            ToIntFunction<MinecraftServer> syncAllPlayers,
            BiConsumer<ServerPlayer, CustomPacketPayload> send) {
        CommandNetwork.syncAllPlayers = Objects.requireNonNull(syncAllPlayers);
        CommandNetwork.send = Objects.requireNonNull(send);
    }

    public static int syncAllPlayers(MinecraftServer server) {
        return syncAllPlayers.applyAsInt(server);
    }

    public static void send(ServerPlayer player, CustomPacketPayload packet) {
        send.accept(player, packet);
    }
}
