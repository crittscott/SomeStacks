package com.github.crittscott.somestacks.client;

import com.github.crittscott.somestacks.network.ConfigSyncPkt;
import com.github.crittscott.somestacks.network.ProtocolPkt;

import net.fabricmc.fabric.api.client.networking.v1.ClientConfigurationConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientConfigurationNetworking;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.network.chat.Component;

/** Fabric client receivers and the packet sender used by the shared gesture rules. */
public final class FabricClientNetworking {
    private FabricClientNetworking() {}

    /**
     * Installs the gesture-state sender, answers the configuration-phase protocol marker and
     * disconnects from a server that lacks it, receives the config snapshot, and hands join and
     * disconnect to {@link SomeStacksClient}. Runs once from the client entry point.
     */
    public static void init() {
        ClientGestures.setSender(packet -> ClientPlayNetworking.send(packet));

        ClientConfigurationNetworking.registerGlobalReceiver(
                ProtocolPkt.TYPE, (payload, context) -> {});
        ClientConfigurationConnectionEvents.START.register((handler, client) -> {
            if (!ClientConfigurationNetworking.canSend(ProtocolPkt.TYPE)) {
                ClientConfigurationNetworking.getSender()
                        .disconnect(Component.translatable("somestacks.disconnect.protocol"));
            }
        });
        ClientPlayNetworking.registerGlobalReceiver(
                ConfigSyncPkt.TYPE,
                (payload, context) -> ClientServerSettings.apply(payload, context.player()));

        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
            SomeStacksClient.onJoin();
        });
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            SomeStacksClient.onDisconnect();
        });
    }
}
