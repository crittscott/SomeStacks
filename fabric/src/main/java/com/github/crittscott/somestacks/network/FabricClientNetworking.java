package com.github.crittscott.somestacks.network;

import com.github.crittscott.somestacks.client.ClientGestures;
import com.github.crittscott.somestacks.client.ClientRenderPacketSink;
import net.fabricmc.fabric.api.client.networking.v1.ClientConfigurationNetworking;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import java.util.function.Consumer;

/** Fabric client receivers and the packet sender used by the shared gesture rules. */
public final class FabricClientNetworking {
    private FabricClientNetworking() {}

    public static void init() {
        ClientGestures.setSender(packet -> ClientPlayNetworking.send(packet));

        ClientConfigurationNetworking.registerGlobalReceiver(
                ProtocolPkt.TYPE, (payload, context) -> { });
        registerClient(ConfigSyncPkt.TYPE, ClientRenderPacketSink::apply);
        registerClient(RenderOverridePkt.TYPE, ClientRenderPacketSink::apply);
        registerClient(WriteOverridesPkt.TYPE, ClientRenderPacketSink::apply);

        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) ->
                ClientGestures.resetSync());
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) ->
                ClientGestures.resetSync());
    }

    private static <T extends CustomPacketPayload> void registerClient(
            CustomPacketPayload.Type<T> type, Consumer<T> handler) {
        ClientPlayNetworking.registerGlobalReceiver(type, (payload, context) ->
                context.client().execute(() -> handler.accept(payload)));
    }
}
