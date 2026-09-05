package com.github.crittscott.somestacks.network;

import com.github.crittscott.somestacks.SomeStacksCommon;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.handling.IPayloadHandler;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * The mod's payloads and their handler registration. Every packet class is shared; this binds the
 * shared {@code TYPE}/{@code STREAM_CODEC} pair to NeoForge's payload system and routes the six
 * client-to-server handlers to their shared {@code handleServer} methods.
 *
 * <p>The three server-to-client payloads are delivered to {@link #clientReceiver}, which the
 * physical client installs from {@code ClientSetup}; the dedicated server never touches a client
 * rendering class. There is no explicit protocol handshake payload: NeoForge already disconnects a
 * peer that has not registered a matching non-optional payload.
 */
public final class ModNetworking {
    private ModNetworking() {}

    private static Consumer<CustomPacketPayload> clientReceiver = payload -> {};

    /** Installed once by {@code ClientSetup} on the physical client. */
    public static void setClientReceiver(Consumer<CustomPacketPayload> receiver) {
        clientReceiver = receiver;
    }

    public static void onRegisterPayloadHandlers(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar(SomeStacksCommon.MODID);

        registrar.playToServer(PlaceAndDepositPkt.TYPE, PlaceAndDepositPkt.STREAM_CODEC,
                toServer(PlaceAndDepositPkt::handleServer));
        registrar.playToServer(DepositPkt.TYPE, DepositPkt.STREAM_CODEC,
                toServer(DepositPkt::handleServer));
        registrar.playToServer(TogglePermanentPkt.TYPE, TogglePermanentPkt.STREAM_CODEC,
                toServer(TogglePermanentPkt::handleServer));
        registrar.playToServer(RotateBlockPkt.TYPE, RotateBlockPkt.STREAM_CODEC,
                toServer(RotateBlockPkt::handleServer));
        registrar.playToServer(RotateItemPkt.TYPE, RotateItemPkt.STREAM_CODEC,
                toServer(RotateItemPkt::handleServer));
        registrar.playToServer(ExtractPkt.TYPE, ExtractPkt.STREAM_CODEC,
                toServer(ExtractPkt::handleServer));

        registrar.playToClient(ConfigSyncPkt.TYPE, ConfigSyncPkt.STREAM_CODEC, ModNetworking::toClient);
        registrar.playToClient(RenderOverridePkt.TYPE, RenderOverridePkt.STREAM_CODEC, ModNetworking::toClient);
        registrar.playToClient(WriteOverridesPkt.TYPE, WriteOverridesPkt.STREAM_CODEC, ModNetworking::toClient);
    }

    private static <T extends CustomPacketPayload> IPayloadHandler<T> toServer(
            BiConsumer<T, ServerPlayer> handler) {
        return (payload, context) -> context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer sender) {
                handler.accept(payload, sender);
            }
        });
    }

    private static void toClient(CustomPacketPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> clientReceiver.accept(payload));
    }
}
