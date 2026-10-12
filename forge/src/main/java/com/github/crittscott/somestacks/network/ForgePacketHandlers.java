package com.github.crittscott.somestacks.network;

import com.github.crittscott.somestacks.client.ItemRenderOverrides;
import com.github.crittscott.somestacks.client.StackState;
import com.github.crittscott.somestacks.renderconfig.ItemRenderConfig;
import com.github.crittscott.somestacks.renderconfig.OverrideJsonCodec;
import com.github.crittscott.somestacks.renderconfig.RenderMode;
import com.github.crittscott.somestacks.util.BlockType;
import java.util.List;
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
        ctx.enqueueWork(() -> {
            if (!msg.isValid()) {
                return;
            }
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> {
                StackState.setBlockEnabled(BlockType.STORAGE_STACK, msg.enableStack());
                StackState.setBlockEnabled(BlockType.SINGLES_STACK, msg.enableSingles());
                StackState.setBlockEnabled(BlockType.BAR_STACK, msg.enableBar());
                ItemRenderOverrides.setSyncedServerOverrides(msg.renderOverrides());
            });
        });
        ctx.setPacketHandled(true);
    }

    static void handleRenderOverride(
            RenderOverridePkt msg, Supplier<NetworkEvent.Context> context) {
        NetworkEvent.Context ctx = context.get();
        ctx.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> {
            if (msg.isReset()) {
                ItemRenderOverrides.removeUser(msg.itemId());
                return;
            }
            RenderMode mode = RenderMode.fromString(msg.renderMode());
            ItemRenderOverrides.putUser(msg.itemId(), OverrideJsonCodec.sanitize(
                    new ItemRenderConfig(mode, msg.scale(), msg.offset())));
        }));
        ctx.setPacketHandled(true);
    }

    static void handleWriteOverrides(
            WriteOverridesPkt msg, Supplier<NetworkEvent.Context> context) {
        NetworkEvent.Context ctx = context.get();
        ctx.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> {
            List<String> namespaces = msg.namespaces();
            if (namespaces == null) {
                return;
            }
            if (namespaces.isEmpty()) {
                ItemRenderOverrides.handleWriteRequest();
            } else {
                ItemRenderOverrides.handleDumpRequest(namespaces);
            }
        }));
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
