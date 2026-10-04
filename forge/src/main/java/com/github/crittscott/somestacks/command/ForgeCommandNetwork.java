package com.github.crittscott.somestacks.command;

import com.github.crittscott.somestacks.SomeStacks;
import com.github.crittscott.somestacks.network.ModNetworking;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.PacketDistributor;

/** Forge delivery for packets initiated by the shared command tree. */
public final class ForgeCommandNetwork implements CommandNetwork.Handler {
    @Override
    public int syncAllPlayers(MinecraftServer server) {
        return SomeStacks.syncAllPlayers(server);
    }

    @Override
    public void send(ServerPlayer player, CustomPacketPayload packet) {
        ModNetworking.CHANNEL.send(packet, PacketDistributor.PLAYER.with(player));
    }
}
