package com.github.crittscott.somestacks.command;

import com.github.crittscott.somestacks.network.FabricNetworking;
import com.github.crittscott.somestacks.network.RenderOverridePkt;
import com.github.crittscott.somestacks.network.WriteOverridesPkt;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/** Fabric delivery for packets initiated by the shared command tree. */
public final class FabricCommandNetwork implements CommandNetwork.Handler {
    @Override
    public int syncAllPlayers(MinecraftServer server) {
        return FabricNetworking.syncAllPlayers(server);
    }

    @Override
    public void send(ServerPlayer player, RenderOverridePkt packet) {
        FabricNetworking.sendRenderOverride(player, packet);
    }

    @Override
    public void send(ServerPlayer player, WriteOverridesPkt packet) {
        FabricNetworking.sendWriteOverrides(player, packet);
    }
}
