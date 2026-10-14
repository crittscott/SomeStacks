package com.github.crittscott.somestacks.network;

import com.github.crittscott.somestacks.SomeStacksCommon;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

import java.util.function.BiConsumer;
import java.util.function.Function;

/** Fabric channels around the shared packet codecs and server handlers. */
public final class FabricNetworking {
    private FabricNetworking() {}

    public static final ResourceLocation PROTOCOL = id("protocol_4");
    public static final ResourceLocation GESTURE_STATE = id("gesture_state");
    public static final ResourceLocation CONFIG_SYNC = id("config_sync");

    public static void initServer() {
        registerServer(GESTURE_STATE, GestureStatePkt::decode, GestureStatePkt::handleServer);
    }

    public static boolean supportsClient(ServerPlayer player) {
        return ServerPlayNetworking.canSend(player, PROTOCOL);
    }

    public static void sendProtocol(ServerPlayer player) {
        ServerPlayNetworking.send(player, PROTOCOL, PacketByteBufs.create());
    }

    public static void sendConfig(ServerPlayer player, ConfigSyncPkt packet) {
        FriendlyByteBuf buf = PacketByteBufs.create();
        ConfigSyncPkt.encode(packet, buf);
        ServerPlayNetworking.send(player, CONFIG_SYNC, buf);
    }

    private static <T> void registerServer(
            ResourceLocation channel, Function<FriendlyByteBuf, T> decoder,
            BiConsumer<T, ServerPlayer> handler) {
        ServerPlayNetworking.registerGlobalReceiver(channel,
                (server, player, networkHandler, buf, responseSender) -> {
                    T packet = decoder.apply(buf);
                    server.execute(() -> handler.accept(packet, player));
                });
    }

    private static ResourceLocation id(String path) {
        return new ResourceLocation(SomeStacksCommon.MODID, path);
    }
}
