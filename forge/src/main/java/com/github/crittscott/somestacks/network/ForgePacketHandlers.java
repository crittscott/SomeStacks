package com.github.crittscott.somestacks.network;

import com.github.crittscott.somestacks.client.ClientRenderPacketSink;
import java.util.function.Consumer;
import java.util.function.Supplier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

/** Forge thread and side adapters around loader-neutral packet codecs and behavior. */
final class ForgePacketHandlers {
    private ForgePacketHandlers() {}

    static void handleGestureState(GestureStatePkt msg, Supplier<NetworkEvent.Context> context) {
        enqueueServer(context, player -> GestureStatePkt.handleServer(msg, player));
    }

    static void handleConfigSync(ConfigSyncPkt msg, Supplier<NetworkEvent.Context> context) {
        NetworkEvent.Context ctx = context.get();
        ctx.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> ClientRenderPacketSink.apply(msg)));
        ctx.setPacketHandled(true);
    }

    private static void enqueueServer(
            Supplier<NetworkEvent.Context> context, Consumer<ServerPlayer> work) {
        NetworkEvent.Context ctx = context.get();
        ctx.enqueueWork(() -> {
            ServerPlayer sender = ctx.getSender();
            if (sender != null) {
                work.accept(sender);
            }
        });
        ctx.setPacketHandled(true);
    }
}
