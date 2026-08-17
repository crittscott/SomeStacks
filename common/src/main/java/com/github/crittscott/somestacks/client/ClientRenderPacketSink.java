package com.github.crittscott.somestacks.client;

import com.github.crittscott.somestacks.network.ConfigSyncPkt;
import com.github.crittscott.somestacks.network.RenderOverridePkt;
import com.github.crittscott.somestacks.network.WriteOverridesPkt;
import com.github.crittscott.somestacks.util.BlockType;
import net.minecraft.resources.ResourceLocation;

import java.util.List;
import java.util.Map;

/** Retains render-related S2C state until the loader's rendering layer is installed. */
public final class ClientRenderPacketSink {
    private ClientRenderPacketSink() {}

    public interface Handler {
        /** Replaces the server-synced render overrides with the newly received set. */
        void setServerOverrides(Map<ResourceLocation, ItemRenderConfig> overrides);

        /** Applies or clears one item's local user override, per {@code packet}. */
        void setUserOverride(RenderOverridePkt packet);

        /**
         * Writes the user override layer to disk if {@code namespaces} is empty, or dumps the
         * resolved render profile of every item in the given namespaces to per-namespace files
         * otherwise.
         */
        void writeOverrides(List<String> namespaces);
    }

    private static Handler handler;
    private static Map<ResourceLocation, ItemRenderConfig> serverOverrides = Map.of();

    public static void setHandler(Handler handler) {
        ClientRenderPacketSink.handler = handler;
        handler.setServerOverrides(serverOverrides);
    }

    public static void apply(ConfigSyncPkt packet) {
        if (!packet.isValid()) {
            return;
        }
        StackState.setBlockEnabled(BlockType.STORAGE_STACK, packet.enableStack());
        StackState.setBlockEnabled(BlockType.SINGLES_STACK, packet.enableSingles());
        StackState.setBlockEnabled(BlockType.BAR_STACK, packet.enableBar());
        serverOverrides = Map.copyOf(packet.renderOverrides());
        if (handler != null) {
            handler.setServerOverrides(serverOverrides);
        }
    }

    public static void apply(RenderOverridePkt packet) {
        if (handler != null) {
            handler.setUserOverride(packet);
        }
    }

    public static void apply(WriteOverridesPkt packet) {
        if (handler != null && packet.namespaces() != null) {
            handler.writeOverrides(packet.namespaces());
        }
    }
}
