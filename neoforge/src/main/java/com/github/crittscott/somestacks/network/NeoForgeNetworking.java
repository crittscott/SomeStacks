package com.github.crittscott.somestacks.network;

import com.github.crittscott.somestacks.SomeStacksCommon;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadHandler;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

import java.util.function.BiConsumer;

/** Registers the shared gesture payload; NeoForge synchronizes policy through its config lifecycle. */
public final class NeoForgeNetworking {
    private NeoForgeNetworking() {}

    public static void onRegisterPayloadHandlers(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar(
                Integer.toString(SomeStacksCommon.PROTOCOL_VERSION));

        registrar.playToServer(GestureStatePkt.TYPE, GestureStatePkt.STREAM_CODEC,
                toServer(GestureStatePkt::handleServer));

    }

    private static <T extends CustomPacketPayload> IPayloadHandler<T> toServer(
            BiConsumer<T, ServerPlayer> handler) {
        return (payload, context) -> handler.accept(payload, (ServerPlayer) context.player());
    }

}
