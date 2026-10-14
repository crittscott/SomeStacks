package com.github.crittscott.somestacks.client;

import com.github.crittscott.somestacks.network.ConfigSyncPkt;
import com.github.crittscott.somestacks.network.RenderOverridePkt;
import com.github.crittscott.somestacks.network.WriteOverridesPkt;
import com.github.crittscott.somestacks.renderconfig.ItemRenderConfig;
import com.github.crittscott.somestacks.util.BlockType;
import java.util.List;
import java.util.Map;
import net.minecraft.resources.ResourceLocation;

/**
 * Retains server configuration overrides and forwards render edits and writes to an installed handler.
 * Packet application runs on the client thread; loaders must dispatch decoded packets there.
 */
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

    /**
     * Installs the nonnull rendering handler and immediately gives it the latest retained server
     * overrides, initially an empty map. Earlier individual-edit and write packets are not replayed.
     */
    public static void setHandler(Handler handler) {
        ClientRenderPacketSink.handler = handler;
        handler.setServerOverrides(serverOverrides);
    }

    /**
     * Applies a valid configuration packet: updates enabled types immediately and retains an
     * immutable copy of its server overrides. Forwards those overrides when a handler is installed;
     * otherwise the next handler installation receives them. Invalid packets leave state unchanged.
     */
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

    /**
     * Forwards an individual override edit to the installed handler. Without a handler the packet
     * is discarded; it is not retained for installation.
     */
    public static void apply(RenderOverridePkt packet) {
        if (handler != null) {
            handler.setUserOverride(packet);
        }
    }

    /**
     * Forwards a write request when a handler is installed and the namespace list is nonnull.
     * Otherwise the request is discarded; it is not retained for installation.
     */
    public static void apply(WriteOverridesPkt packet) {
        if (handler != null && packet.namespaces() != null) {
            handler.writeOverrides(packet.namespaces());
        }
    }
}
