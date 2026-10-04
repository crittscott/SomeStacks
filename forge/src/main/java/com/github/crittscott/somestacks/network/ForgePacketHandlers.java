package com.github.crittscott.somestacks.network;

import com.github.crittscott.somestacks.client.ClientRenderPacketSink;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.network.CustomPayloadEvent;
import net.minecraftforge.fml.DistExecutor;

import java.util.Objects;
import java.util.function.Consumer;

/**
 * Forge side adapters around the loader-neutral packet codecs and behavior. Registered through
 * {@code addMain}, so the channel already runs each handler on the receiving side's main thread and
 * marks the packet handled; these only unwrap the sender and dispatch. Client-only work stays behind
 * {@link DistExecutor} so a dedicated server never loads a rendering class.
 */
final class ForgePacketHandlers {
    private ForgePacketHandlers() {}

    static void handleGestureState(GestureStatePkt msg, CustomPayloadEvent.Context ctx) {
        server(ctx, player -> GestureStatePkt.handleServer(msg, player));
    }

    static void handleConfigSync(ConfigSyncPkt msg, CustomPayloadEvent.Context ctx) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientRenderPacketSink.apply(msg));
    }

    static void handleRenderOverride(RenderOverridePkt msg, CustomPayloadEvent.Context ctx) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientRenderPacketSink.apply(msg));
    }

    static void handleWriteOverrides(WriteOverridesPkt msg, CustomPayloadEvent.Context ctx) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientRenderPacketSink.apply(msg));
    }

    private static void server(CustomPayloadEvent.Context ctx, Consumer<ServerPlayer> work) {
        work.accept(Objects.requireNonNull(ctx.getSender(),
                "Serverbound payload has no sending player"));
    }
}
