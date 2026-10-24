package com.github.crittscott.somestacks.server;

import com.mojang.authlib.GameProfile;
import net.minecraft.network.Connection;
import net.minecraft.network.PacketListener;
import net.minecraft.network.PacketSendListener;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.network.ServerGamePacketListenerImpl;

import javax.annotation.Nullable;

/** A server-only player whose native event callbacks can safely send correction packets. */
public final class SyntheticPlayer extends ServerPlayer {
    public SyntheticPlayer(ServerLevel level, GameProfile profile) {
        super(level.getServer(), level, profile, ClientInformation.createDefault());
        connection = new ServerGamePacketListenerImpl(level.getServer(), new Connection(PacketFlow.SERVERBOUND) {
            @Override
            public void setListenerForServerboundHandshake(PacketListener listener) {}
        }, this, CommonListenerCookie.createInitial(profile, false)) {
            @Override
            public void send(Packet<?> packet) {}

            @Override
            public void send(Packet<?> packet, @Nullable PacketSendListener listener) {}
        };
    }

    @Override
    public void sendSystemMessage(Component message) {}

    @Override
    public void displayClientMessage(Component message, boolean actionBar) {}
}
