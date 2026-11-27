package com.github.crittscott.somestacks.client;

import com.github.crittscott.somestacks.network.ConfigSyncPkt;
import com.github.crittscott.somestacks.network.FabricNetworking;
import com.github.crittscott.somestacks.network.GestureStatePkt;

import net.fabricmc.fabric.api.client.networking.v1.ClientLoginConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientLoginNetworking;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.concurrent.CompletableFuture;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;

/** Fabric client receivers and the packet sender used by the shared gesture rules. */
public final class FabricClientNetworking {
    private static volatile boolean handshakeAccepted;

    private FabricClientNetworking() {}

    /**
     * Installs the gesture-state sender and the client receivers: the login handshake records
     * whether the server's protocol version matched, policy snapshots apply on the client thread,
     * and joining a server whose handshake did not match disconnects. Gesture sync resets on every
     * join and disconnect. Runs once from the client entry point.
     */
    public static void init() {
        ClientGestures.setSender(
                packet -> send(FabricNetworking.GESTURE_STATE, packet, GestureStatePkt::encode));

        ClientLoginConnectionEvents.INIT.register((handler, client) -> handshakeAccepted = false);
        ClientLoginConnectionEvents.DISCONNECT.register(
                (handler, client) -> handshakeAccepted = false);
        ClientLoginNetworking.registerGlobalReceiver(
                FabricNetworking.HANDSHAKE,
                (client, handler, buf, listenerAdder) -> {
                    handshakeAccepted = FabricNetworking.matchesProtocol(buf);
                    return CompletableFuture.completedFuture(FabricNetworking.protocolPayload());
                });
        registerClient(
                FabricNetworking.CONFIG_SYNC,
                ConfigSyncPkt::decode,
                packet -> ClientServerSettings.apply(packet, Minecraft.getInstance().player));

        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            ClientGestures.resetSync();
            handshakeAccepted = false;
        });
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
            ClientGestures.resetSync();
            if (!handshakeAccepted) {
                handler.getConnection()
                        .disconnect(Component.translatable("somestacks.disconnect.protocol"));
            }
        });
    }

    private static <T> void registerClient(
            ResourceLocation channel, Function<FriendlyByteBuf, T> decoder, Consumer<T> handler) {
        ClientPlayNetworking.registerGlobalReceiver(
                channel,
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
