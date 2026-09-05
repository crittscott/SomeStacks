package com.github.crittscott.somestacks.command;

import com.github.crittscott.somestacks.SomeStacksNeoForge;
import com.github.crittscott.somestacks.network.RenderOverridePkt;
import com.github.crittscott.somestacks.network.WriteOverridesPkt;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;

/** NeoForge delivery for packets initiated by the shared command tree. */
public final class NeoForgeCommandNetwork implements CommandNetwork.Handler {
    @Override
    public int syncAllPlayers(MinecraftServer server) {
        return SomeStacksNeoForge.syncAllPlayers(server);
    }

    @Override
    public void send(ServerPlayer player, RenderOverridePkt packet) {
        PacketDistributor.sendToPlayer(player, packet);
    }

    @Override
    public void send(ServerPlayer player, WriteOverridesPkt packet) {
        PacketDistributor.sendToPlayer(player, packet);
    }
}
