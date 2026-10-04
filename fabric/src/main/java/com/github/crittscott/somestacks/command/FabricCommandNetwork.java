package com.github.crittscott.somestacks.command;

import com.github.crittscott.somestacks.network.FabricNetworking;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/** Fabric delivery for packets initiated by the shared command tree. */
public final class FabricCommandNetwork implements CommandNetwork.Handler {
    @Override
    public int syncAllPlayers(MinecraftServer server) {
        return FabricNetworking.syncAllPlayers(server);
    }

    @Override
    public void send(ServerPlayer player, CustomPacketPayload packet) {
        FabricNetworking.send(player, packet);
    }
}
