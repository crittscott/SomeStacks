package com.github.crittscott.somestacks.network;

import com.github.crittscott.somestacks.client.ClientGestures;
import com.github.crittscott.somestacks.client.ClientRenderPacketSink;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;

/** Fabric client receivers and the packet sender used by the shared gesture rules. */
public final class FabricClientNetworking {
    private FabricClientNetworking() {}

    public static void init() {
        ClientGestures.setSender(packet -> send(
                FabricNetworking.GESTURE_STATE, packet, GestureStatePkt::encode));

        ClientPlayNetworking.registerGlobalReceiver(FabricNetworking.PROTOCOL,
                (client, handler, buf, responseSender) -> { });
        registerClient(FabricNetworking.CONFIG_SYNC, ConfigSyncPkt::decode,
                ClientRenderPacketSink::apply);
        registerClient(FabricNetworking.RENDER_OVERRIDE, RenderOverridePkt::decode,
                ClientRenderPacketSink::apply);
        registerClient(FabricNetworking.WRITE_OVERRIDES, WriteOverridesPkt::decode,
                ClientRenderPacketSink::apply);

        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> ClientGestures.resetSync());
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
            ClientGestures.resetSync();
            if (!ClientPlayNetworking.canSend(FabricNetworking.GESTURE_STATE)) {
                handler.getConnection().disconnect(
                        Component.translatable("somestacks.disconnect.protocol"));
            }
        });
    }

    private static <T> void registerClient(
            ResourceLocation channel, Function<FriendlyByteBuf, T> decoder,
            Consumer<T> handler) {
        ClientPlayNetworking.registerGlobalReceiver(channel,
                (client, networkHandler, buf, responseSender) -> {
                    T packet = decoder.apply(buf);
                    client.execute(() -> handler.accept(packet));
                });
    }

    private static <T> void send(
            ResourceLocation channel, T packet, BiConsumer<T, FriendlyByteBuf> encoder) {
        FriendlyByteBuf buf = PacketByteBufs.create();
        encoder.accept(packet, buf);
        ClientPlayNetworking.send(channel, buf);
    }
}
