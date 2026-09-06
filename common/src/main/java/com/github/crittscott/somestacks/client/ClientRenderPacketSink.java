package com.github.crittscott.somestacks.client;

import com.github.crittscott.somestacks.network.ConfigSyncPkt;
import com.github.crittscott.somestacks.network.RenderOverridePkt;
import com.github.crittscott.somestacks.network.WriteOverridesPkt;
import com.github.crittscott.somestacks.util.BlockType;
import com.github.crittscott.somestacks.util.OverrideJsonCodec;

import java.util.List;

/** Applies render-related S2C packets to the shared client state. */
public final class ClientRenderPacketSink {
    private ClientRenderPacketSink() {}

    public static void apply(ConfigSyncPkt packet) {
        if (!packet.isValid()) {
            return;
        }
        StackState.setBlockEnabled(BlockType.STORAGE_STACK, packet.enableStack());
        StackState.setBlockEnabled(BlockType.SINGLES_STACK, packet.enableSingles());
        StackState.setBlockEnabled(BlockType.BAR_STACK, packet.enableBar());
        ItemRenderOverrides.setSyncedServerOverrides(packet.renderOverrides());
    }

    public static void apply(RenderOverridePkt packet) {
        if (packet.isReset()) {
            ItemRenderOverrides.removeUser(packet.itemId());
            return;
        }
        RenderMode mode = RenderMode.fromString(packet.renderMode());
        ItemRenderOverrides.putUser(packet.itemId(), OverrideJsonCodec.sanitize(
                new ItemRenderConfig(mode, packet.scale(), packet.offset())));
    }

    public static void apply(WriteOverridesPkt packet) {
        List<String> namespaces = packet.namespaces();
        if (namespaces == null) {
            return;
        }
        if (namespaces.isEmpty()) {
            ItemRenderOverrides.handleWriteRequest();
        } else {
            ItemRenderOverrides.handleDumpRequest(namespaces);
        }
    }
}
