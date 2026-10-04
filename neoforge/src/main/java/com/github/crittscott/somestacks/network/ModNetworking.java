package com.github.crittscott.somestacks.network;

import com.github.crittscott.somestacks.SomeStacksCommon;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.handling.IPayloadHandler;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * The mod's payloads and their handler registration. Every packet class is shared; this binds the
 * shared {@code TYPE}/{@code STREAM_CODEC} pair to NeoForge's payload system.
 *
 * <p>The three server-to-client payloads are delivered to {@link #clientReceiver}, which the
 * physical client installs from {@code ClientSetup}; the dedicated server never touches a client
 * rendering class. NeoForge rejects peers whose registrar version does not match the shared
 * protocol version or which omit a required payload.
 */
public final class ModNetworking {
    private ModNetworking() {}

    private static Consumer<CustomPacketPayload> clientReceiver;

    /** Installed once by {@code ClientSetup} on the physical client. */
    public static void setClientReceiver(Consumer<CustomPacketPayload> receiver) {
        clientReceiver = Objects.requireNonNull(receiver);
    }

    public static void onRegisterPayloadHandlers(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar(
                Integer.toString(SomeStacksCommon.PROTOCOL_VERSION));

        registrar.playToServer(GestureStatePkt.TYPE, GestureStatePkt.STREAM_CODEC,
                toServer(GestureStatePkt::handleServer));

        registrar.playToClient(ConfigSyncPkt.TYPE, ConfigSyncPkt.STREAM_CODEC, ModNetworking::toClient);
        registrar.playToClient(RenderOverridePkt.TYPE, RenderOverridePkt.STREAM_CODEC, ModNetworking::toClient);
        registrar.playToClient(WriteOverridesPkt.TYPE, WriteOverridesPkt.STREAM_CODEC, ModNetworking::toClient);
    }

    private static <T extends CustomPacketPayload> IPayloadHandler<T> toServer(
            BiConsumer<T, ServerPlayer> handler) {
        return (payload, context) -> handler.accept(payload, (ServerPlayer) context.player());
    }

    private static void toClient(CustomPacketPayload payload, IPayloadContext context) {
        Objects.requireNonNull(clientReceiver,
                "Client packet receiver has not been installed").accept(payload);
    }
}
