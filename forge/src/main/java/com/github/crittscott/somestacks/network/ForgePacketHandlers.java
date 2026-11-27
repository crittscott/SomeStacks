package com.github.crittscott.somestacks.network;

import com.github.crittscott.somestacks.client.ClientRenderPacketSink;

import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.network.CustomPayloadEvent;
import net.minecraftforge.fml.DistExecutor;

/**
 * Forge side adapters around the loader-neutral packet codecs and behavior. Registered through
 * {@code addMain}, so the channel already runs each handler on the receiving side's main thread and
 * marks the packet handled; these only unwrap the sender and dispatch. Client-only work stays
 * behind {@link DistExecutor} so a dedicated server never loads a rendering class.
 */
final class ForgePacketHandlers {
    private ForgePacketHandlers() {}

    static void handleGestureState(GestureStatePkt msg, CustomPayloadEvent.Context ctx) {
        GestureStatePkt.handleServer(msg, ctx.getSender());
    }

    static void handleClient(ConfigSyncPkt msg, CustomPayloadEvent.Context ctx) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> Client.apply(msg));
    }

    /**
     * Passing {@code Minecraft.getInstance().player} as a {@code Player} makes the verifier load
     * {@code LocalPlayer} to check the widening, which fails on a dedicated server. Keeping that call
     * in this nested class means only the client ever verifies it.
     */
    private static final class Client {
        static void apply(ConfigSyncPkt msg) {
            ClientRenderPacketSink.apply(msg, Minecraft.getInstance().player);
        }
    }
}
