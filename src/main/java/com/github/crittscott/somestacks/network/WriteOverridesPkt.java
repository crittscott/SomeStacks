package com.github.crittscott.somestacks.network;

import com.github.crittscott.somestacks.client.ItemRenderOverrides;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Server to client: asks the receiving client to write its user render override layer
 * to its override file, as directed by the {@code ss write} command.
 */
public class WriteOverridesPkt {
    public static void encode(WriteOverridesPkt msg, FriendlyByteBuf buf) {
    }

    public static WriteOverridesPkt decode(FriendlyByteBuf buf) {
        return new WriteOverridesPkt();
    }

    public static void handle(WriteOverridesPkt msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> ItemRenderOverrides::handleWriteRequest));
        ctx.get().setPacketHandled(true);
    }
}
