package com.github.crittscott.somestacks.network;

import com.github.crittscott.somestacks.client.ClientGestures;
import com.github.crittscott.somestacks.client.ClientRenderPacketSink;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import java.util.function.Consumer;

/** Fabric client receivers and the packet sender used by the shared gesture rules. */
public final class FabricClientNetworking implements ClientGestures.Sender {
    private FabricClientNetworking() {}

    public static void init() {
        ClientGestures.setSender(new FabricClientNetworking());

        ClientPlayNetworking.registerGlobalReceiver(ProtocolPkt.TYPE, (payload, context) -> { });
        registerClient(ConfigSyncPkt.TYPE, ClientRenderPacketSink::apply);
        registerClient(RenderOverridePkt.TYPE, ClientRenderPacketSink::apply);
        registerClient(WriteOverridesPkt.TYPE, ClientRenderPacketSink::apply);

        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
            if (!ClientPlayNetworking.canSend(PlaceAndDepositPkt.TYPE)) {
                handler.getConnection().disconnect(
                        Component.translatable("somestacks.disconnect.protocol"));
            }
        });
    }

    @Override
    public void send(CustomPacketPayload payload) {
        ClientPlayNetworking.send(payload);
    }

    private static <T extends CustomPacketPayload> void registerClient(
            CustomPacketPayload.Type<T> type, Consumer<T> handler) {
        ClientPlayNetworking.registerGlobalReceiver(type, (payload, context) ->
                context.client().execute(() -> handler.accept(payload)));
    }
}
