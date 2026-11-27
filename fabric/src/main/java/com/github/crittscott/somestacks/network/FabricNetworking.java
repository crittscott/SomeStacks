package com.github.crittscott.somestacks.network;

import com.github.crittscott.somestacks.SomeStacksCommon;

import io.netty.handler.codec.DecoderException;

import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerLoginConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerLoginNetworking;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.function.BiConsumer;
import java.util.function.Function;

/** Fabric channels around the shared packet codecs and server handlers. */
public final class FabricNetworking {
    private FabricNetworking() {}

    public static final ResourceLocation HANDSHAKE = id("handshake");
    private static final int MAX_PROTOCOL_LENGTH = 16;
    private static final String PROTOCOL = String.valueOf(SomeStacksCommon.PROTOCOL_VERSION);
    public static final ResourceLocation GESTURE_STATE = id("gesture_state");
    public static final ResourceLocation CONFIG_SYNC = id("config_sync");

    /**
     * Registers the server half of the protocol: every login is queried with the protocol version
     * and disconnected, with an English fallback message, when the client does not understand the
     * query or answers a different version; the gesture-state receiver decodes on the network
     * thread and runs its handler on the server thread. Runs once from the common entry point.
     */
    public static void initServer() {
        ServerLoginConnectionEvents.QUERY_START.register((handler, server, sender, synchronizer) ->
                sender.sendPacket(HANDSHAKE, protocolPayload()));
        ServerLoginNetworking.registerGlobalReceiver(
                HANDSHAKE,
                (server, handler, understood, buf, synchronizer, responseSender) -> {
                    if (!understood || !matchesProtocol(buf)) {
                        handler.disconnect(
                                Component.translatableWithFallback(
                                        "somestacks.disconnect.protocol",
                                        "Some Stacks protocol mismatch: the mod is required on both client and server"));
                    }
                });
        registerServer(GESTURE_STATE, GestureStatePkt::decode, GestureStatePkt::handleServer);
    }

    /** A fresh handshake payload holding this side's protocol version, for either direction. */
    public static FriendlyByteBuf protocolPayload() {
        FriendlyByteBuf buf = PacketByteBufs.create();
        buf.writeUtf(PROTOCOL, MAX_PROTOCOL_LENGTH);
        return buf;
    }

    /**
     * Whether a received handshake payload is exactly this side's protocol version with nothing
     * after it; a malformed payload is a mismatch, not an error.
     */
    public static boolean matchesProtocol(FriendlyByteBuf buf) {
        try {
            return PROTOCOL.equals(buf.readUtf(MAX_PROTOCOL_LENGTH)) && !buf.isReadable();
        } catch (DecoderException | IndexOutOfBoundsException malformed) {
            return false;
        }
    }

    /** Encodes the snapshot once and sends that one packet to every connected player. */
    public static void broadcastConfig(MinecraftServer server, ConfigSyncPkt packet) {
        server.getPlayerList()
                .broadcastAll(
                        ServerPlayNetworking.createS2CPacket(CONFIG_SYNC, configPayload(packet)));
    }

    private static FriendlyByteBuf configPayload(ConfigSyncPkt packet) {
        FriendlyByteBuf buf = PacketByteBufs.create();
        ConfigSyncPkt.encode(packet, buf);
        return buf;
    }

    /** Sends one player a policy snapshot; requires the player's play connection. */
    public static void sendConfig(ServerPlayer player, ConfigSyncPkt packet) {
        ServerPlayNetworking.send(player, CONFIG_SYNC, configPayload(packet));
    }

    private static <T> void registerServer(
            ResourceLocation channel,
            Function<FriendlyByteBuf, T> decoder,
            BiConsumer<T, ServerPlayer> handler) {
        ServerPlayNetworking.registerGlobalReceiver(
                channel,
                (server, player, networkHandler, buf, responseSender) -> {
                    T packet = decoder.apply(buf);
                    server.execute(() -> handler.accept(packet, player));
                });
    }

    private static ResourceLocation id(String path) {
        return new ResourceLocation(SomeStacksCommon.MODID, path);
    }
}
