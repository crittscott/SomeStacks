package com.github.crittscott.somestacks.network;

import com.github.crittscott.somestacks.ServerConfig;
import io.netty.handler.codec.DecoderException;
import net.minecraft.network.FriendlyByteBuf;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Complete server policy used by client mode cycling and local render-authoring commands. */
public record ConfigSyncPkt(
        boolean enableStack,
        boolean enableSingles,
        boolean enableBar,
        List<String> genMods,
        List<String> disabledMods) {

    // Valid namespaces are ASCII: at most two length bytes and MAX_NAMESPACE_LENGTH bytes each.
    public static final int MAX_PACKET_BYTES = 3 + 2 * (2 + ServerConfig.MAX_NAMESPACE_ENTRIES
            * (2 + ServerConfig.MAX_NAMESPACE_LENGTH));

    public ConfigSyncPkt {
        genMods = List.copyOf(genMods);
        disabledMods = List.copyOf(disabledMods);
    }

    /** Copies already prepared policy without changing it or emitting per-login diagnostics. */
    public static ConfigSyncPkt snapshot(ServerConfig.Settings settings) {
        return new ConfigSyncPkt(settings.enableStorageStackBlock(), settings.enableSinglesStackBlock(),
                settings.enableBarStackBlock(), settings.genMods(), settings.disableMods());
    }

    public static void encode(ConfigSyncPkt packet, FriendlyByteBuf buf) {
        buf.writeBoolean(packet.enableStack);
        buf.writeBoolean(packet.enableSingles);
        buf.writeBoolean(packet.enableBar);
        buf.writeCollection(packet.genMods,
                (out, namespace) -> out.writeUtf(namespace, ServerConfig.MAX_NAMESPACE_LENGTH));
        buf.writeCollection(packet.disabledMods,
                (out, namespace) -> out.writeUtf(namespace, ServerConfig.MAX_NAMESPACE_LENGTH));
    }

    /** Validates the entire snapshot on the network thread before any client state is changed. */
    public static ConfigSyncPkt decode(FriendlyByteBuf buf) {
        if (buf.readableBytes() > MAX_PACKET_BYTES) {
            throw new DecoderException("Config synchronization packet exceeds byte limit");
        }
        boolean enableStack = buf.readBoolean();
        boolean enableSingles = buf.readBoolean();
        boolean enableBar = buf.readBoolean();
        List<String> genMods = readNamespaces(buf);
        List<String> disabledMods = readNamespaces(buf);
        if (buf.isReadable()) {
            throw new DecoderException("Trailing config synchronization data");
        }
        return new ConfigSyncPkt(enableStack, enableSingles, enableBar, genMods, disabledMods);
    }

    private static List<String> readNamespaces(FriendlyByteBuf buf) {
        int count = buf.readVarInt();
        if (count < 0 || count > ServerConfig.MAX_NAMESPACE_ENTRIES) {
            throw new DecoderException("Invalid synchronized namespace count: " + count);
        }
        List<String> namespaces = new ArrayList<>(count);
        Set<String> seen = new HashSet<>();
        for (int i = 0; i < count; i++) {
            String namespace = buf.readUtf(ServerConfig.MAX_NAMESPACE_LENGTH);
            if (!ServerConfig.isValidNamespace(namespace)
                    || !seen.add(namespace)) {
                throw new DecoderException("Invalid or duplicate synchronized namespace");
            }
            namespaces.add(namespace);
        }
        return namespaces;
    }
}
