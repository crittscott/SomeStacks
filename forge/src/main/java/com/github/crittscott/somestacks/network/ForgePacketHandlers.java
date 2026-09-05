package com.github.crittscott.somestacks.network;

import com.github.crittscott.somestacks.client.ItemRenderConfig;
import com.github.crittscott.somestacks.client.ItemRenderOverrides;
import com.github.crittscott.somestacks.client.RenderMode;
import com.github.crittscott.somestacks.client.StackState;
import com.github.crittscott.somestacks.util.BlockType;
import com.github.crittscott.somestacks.util.OverrideJsonCodec;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.network.CustomPayloadEvent;
import net.minecraftforge.fml.DistExecutor;

import java.util.List;
import java.util.function.Consumer;

/**
 * Forge side adapters around the loader-neutral packet codecs and behavior. Registered through
 * {@code addMain}, so the channel already runs each handler on the receiving side's main thread and
 * marks the packet handled; these only unwrap the sender and dispatch. Client-only work stays behind
 * {@link DistExecutor} so a dedicated server never loads a rendering class.
 */
final class ForgePacketHandlers {
    private ForgePacketHandlers() {}

    static void handlePlaceAndDeposit(PlaceAndDepositPkt msg, CustomPayloadEvent.Context ctx) {
        server(ctx, player -> PlaceAndDepositPkt.handleServer(msg, player));
    }

    static void handleDeposit(DepositPkt msg, CustomPayloadEvent.Context ctx) {
        server(ctx, player -> DepositPkt.handleServer(msg, player));
    }

    static void handleTogglePermanent(TogglePermanentPkt msg, CustomPayloadEvent.Context ctx) {
        server(ctx, player -> TogglePermanentPkt.handleServer(msg, player));
    }

    static void handleRotateBlock(RotateBlockPkt msg, CustomPayloadEvent.Context ctx) {
        server(ctx, player -> RotateBlockPkt.handleServer(msg, player));
    }

    static void handleRotateItem(RotateItemPkt msg, CustomPayloadEvent.Context ctx) {
        server(ctx, player -> RotateItemPkt.handleServer(msg, player));
    }

    static void handleExtract(ExtractPkt msg, CustomPayloadEvent.Context ctx) {
        server(ctx, player -> ExtractPkt.handleServer(msg, player));
    }

    static void handleConfigSync(ConfigSyncPkt msg, CustomPayloadEvent.Context ctx) {
        if (!msg.isValid()) {
            return;
        }
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> {
            StackState.setBlockEnabled(BlockType.STORAGE_STACK, msg.enableStack());
            StackState.setBlockEnabled(BlockType.SINGLES_STACK, msg.enableSingles());
            StackState.setBlockEnabled(BlockType.BAR_STACK, msg.enableBar());
            ItemRenderOverrides.setSyncedServerOverrides(msg.renderOverrides());
        });
    }

    static void handleRenderOverride(RenderOverridePkt msg, CustomPayloadEvent.Context ctx) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> {
            if (msg.isReset()) {
                ItemRenderOverrides.removeUser(msg.itemId());
                return;
            }
            RenderMode mode = RenderMode.fromString(msg.renderMode());
            ItemRenderOverrides.putUser(msg.itemId(), OverrideJsonCodec.sanitize(
                    new ItemRenderConfig(mode, msg.scale(), msg.offset())));
        });
    }

    static void handleWriteOverrides(WriteOverridesPkt msg, CustomPayloadEvent.Context ctx) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> {
            List<String> namespaces = msg.namespaces();
            if (namespaces == null) {
                return;
            }
            if (namespaces.isEmpty()) {
                ItemRenderOverrides.handleWriteRequest();
            } else {
                ItemRenderOverrides.handleDumpRequest(namespaces);
            }
        });
    }

    private static void server(CustomPayloadEvent.Context ctx, Consumer<ServerPlayer> work) {
        ServerPlayer sender = ctx.getSender();
        if (sender != null) {
            work.accept(sender);
        }
    }
}
