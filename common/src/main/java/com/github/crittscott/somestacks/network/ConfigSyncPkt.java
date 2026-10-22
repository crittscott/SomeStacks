package com.github.crittscott.somestacks.network;

import com.github.crittscott.somestacks.ServerConfig;
import com.github.crittscott.somestacks.SomeStacksCommon;
import io.netty.handler.codec.DecoderException;
import net.minecraft.network.FriendlyByteBuf;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;

/**
 * One byte-bounded chunk of the server settings synchronized to clients: which stack types are
 * enabled, and the gallery and disabled namespace lists the client-local {@code ss write} forms
 * read. A snapshot is one generation of chunks, first to last, sent on login and whenever policy
 * changes.
 *
 * <p>The disabled-item list and pile limits are not sent. They gate server-side decisions only,
 * and the client never needs to predict them.
 */
public record ConfigSyncPkt(
        int generation,
        boolean first,
        boolean last,
        boolean enableStack,
        boolean enableSingles,
        boolean enableBar,
        int totalGenMods,
        int totalDisabledMods,
        List<String> genMods,
        List<String> disabledMods) {

    public static final int MAX_PACKET_BYTES = 256 * 1024;
    private static final int PACKET_OVERHEAD_BYTES = 64;
    private static final int MAX_TOTAL_NAMESPACES = 4_096;
    private static final int MAX_NAMESPACE_LENGTH = 256;
    private static final int MAX_ENCODED_NAMESPACE_BYTES = MAX_NAMESPACE_LENGTH + 2;
    private static final int MAX_NAMESPACE_BYTES = 2 * MAX_TOTAL_NAMESPACES * MAX_ENCODED_NAMESPACE_BYTES;
    public static final int MAX_GENERATION_CHUNKS = (MAX_NAMESPACE_BYTES
            + MAX_PACKET_BYTES - PACKET_OVERHEAD_BYTES - MAX_ENCODED_NAMESPACE_BYTES - 1)
            / (MAX_PACKET_BYTES - PACKET_OVERHEAD_BYTES - MAX_ENCODED_NAMESPACE_BYTES);
    public static final int MAX_GENERATION_BYTES = MAX_NAMESPACE_BYTES
            + MAX_GENERATION_CHUNKS * PACKET_OVERHEAD_BYTES;

    private static int nextGeneration;

    public ConfigSyncPkt {
        genMods = List.copyOf(genMods);
        disabledMods = List.copyOf(disabledMods);
        if (generation <= 0 || totalGenMods < 0 || totalGenMods > MAX_TOTAL_NAMESPACES
                || totalDisabledMods < 0 || totalDisabledMods > MAX_TOTAL_NAMESPACES
                || genMods.size() > totalGenMods || disabledMods.size() > totalDisabledMods) {
            throw new DecoderException("Invalid config synchronization totals");
        }
        validateNamespaces(genMods);
        validateNamespaces(disabledMods);
        if (budgetBytes(genMods, disabledMods) > MAX_PACKET_BYTES) {
            throw new DecoderException("Config synchronization packet exceeds byte limit");
        }
    }

    /** Builds a new generation on the server thread describing the given policy, in send order. */
    public static List<ConfigSyncPkt> snapshot(ServerConfig.Settings settings) {
        nextGeneration = nextGeneration == Integer.MAX_VALUE ? 1 : nextGeneration + 1;
        return buildPackets(
                nextGeneration,
                settings.enableStorageStackBlock(),
                settings.enableSinglesStackBlock(),
                settings.enableBarStackBlock(),
                boundedNamespaces(settings.genMods(), "gallery mod"),
                boundedNamespaces(settings.disableMods(), "disabled mod"));
    }

    private static List<String> boundedNamespaces(List<String> source, String label) {
        LinkedHashSet<String> result = new LinkedHashSet<>();
        for (String entry : source) {
            String namespace = entry.trim().toLowerCase(Locale.ROOT);
            if (namespace.isEmpty() || result.contains(namespace)) {
                continue;
            }
            if (!isValidNamespace(namespace)) {
                SomeStacksCommon.LOGGER.warn(
                        "Ignoring invalid {} namespace '{}' in client synchronization", label, entry);
                continue;
            }
            if (result.size() == MAX_TOTAL_NAMESPACES) {
                SomeStacksCommon.LOGGER.warn(
                        "The {} list exceeds the synchronization limit of {}; remaining entries "
                                + "will not be sent",
                        label, MAX_TOTAL_NAMESPACES);
                break;
            }
            result.add(namespace);
        }
        return List.copyOf(result);
    }

    private static List<ConfigSyncPkt> buildPackets(
            int generation,
            boolean enableStack,
            boolean enableSingles,
            boolean enableBar,
            List<String> genMods,
            List<String> disabledMods) {
        List<Chunk> chunks = new ArrayList<>();
        Chunk chunk = new Chunk();

        for (String namespace : genMods) {
            int size = encodedStringSize(namespace);
            if (!chunk.isEmpty() && chunk.encodedBytes + size > MAX_PACKET_BYTES) {
                chunks.add(chunk);
                chunk = new Chunk();
            }
            chunk.genMods.add(namespace);
            chunk.encodedBytes += size;
        }
        for (String namespace : disabledMods) {
            int size = encodedStringSize(namespace);
            if (!chunk.isEmpty() && chunk.encodedBytes + size > MAX_PACKET_BYTES) {
                chunks.add(chunk);
                chunk = new Chunk();
            }
            chunk.disabledMods.add(namespace);
            chunk.encodedBytes += size;
        }
        if (!chunk.isEmpty() || chunks.isEmpty()) {
            chunks.add(chunk);
        }

        List<ConfigSyncPkt> packets = new ArrayList<>(chunks.size());
        for (int i = 0; i < chunks.size(); i++) {
            Chunk current = chunks.get(i);
            packets.add(new ConfigSyncPkt(
                    generation,
                    i == 0,
                    i == chunks.size() - 1,
                    enableStack,
                    enableSingles,
                    enableBar,
                    genMods.size(),
                    disabledMods.size(),
                    current.genMods,
                    current.disabledMods));
        }
        return List.copyOf(packets);
    }

    public static void encode(ConfigSyncPkt packet, FriendlyByteBuf buf) {
        buf.writeVarInt(packet.generation);
        buf.writeBoolean(packet.first);
        buf.writeBoolean(packet.last);
        buf.writeBoolean(packet.enableStack);
        buf.writeBoolean(packet.enableSingles);
        buf.writeBoolean(packet.enableBar);
        buf.writeVarInt(packet.totalGenMods);
        buf.writeVarInt(packet.totalDisabledMods);

        writeNamespaces(buf, packet.genMods);
        writeNamespaces(buf, packet.disabledMods);
    }

    /** Decodes one chunk, rejecting anything malformed or over budget before it reaches the client. */
    public static ConfigSyncPkt decode(FriendlyByteBuf buf) {
        if (buf.readableBytes() > MAX_PACKET_BYTES) {
            throw new DecoderException("Config synchronization packet exceeds byte limit");
        }
        int generation = buf.readVarInt();
        if (generation <= 0) {
            throw new DecoderException("Invalid config generation: " + generation);
        }
        boolean first = buf.readBoolean();
        boolean last = buf.readBoolean();
        boolean enableStack = buf.readBoolean();
        boolean enableSingles = buf.readBoolean();
        boolean enableBar = buf.readBoolean();
        int totalGenMods = readBoundedCount(buf, MAX_TOTAL_NAMESPACES, "gallery mod total");
        int totalDisabledMods = readBoundedCount(
                buf, MAX_TOTAL_NAMESPACES, "disabled mod total");

        List<String> genMods = readNamespaces(buf, totalGenMods, "gallery mod chunk");
        List<String> disabledMods = readNamespaces(
                buf, totalDisabledMods, "disabled mod chunk");
        return new ConfigSyncPkt(
                generation, first, last, enableStack, enableSingles, enableBar,
                totalGenMods, totalDisabledMods,
                genMods, disabledMods);
    }

    /** Conservative encoded size, including bounded metadata overhead. */
    public int budgetBytes() {
        return budgetBytes(genMods, disabledMods);
    }

    private static int budgetBytes(List<String> genMods, List<String> disabledMods) {
        int bytes = PACKET_OVERHEAD_BYTES;
        for (String namespace : genMods) bytes += encodedStringSize(namespace);
        for (String namespace : disabledMods) bytes += encodedStringSize(namespace);
        return bytes;
    }

    private static boolean isValidNamespace(String namespace) {
        return !namespace.isEmpty() && namespace.length() <= MAX_NAMESPACE_LENGTH
                && namespace.chars().allMatch(c -> c == '_' || c == '-' || c == '.'
                        || (c >= 'a' && c <= 'z') || (c >= '0' && c <= '9'));
    }

    private static void validateNamespaces(List<String> namespaces) {
        for (String namespace : namespaces) {
            if (!isValidNamespace(namespace)) {
                throw new DecoderException("Invalid synchronized namespace");
            }
        }
    }

    private static void writeNamespaces(FriendlyByteBuf buf, List<String> namespaces) {
        buf.writeVarInt(namespaces.size());
        for (String namespace : namespaces) {
            buf.writeUtf(namespace, MAX_NAMESPACE_LENGTH);
        }
    }

    private static List<String> readNamespaces(FriendlyByteBuf buf, int total, String description) {
        int count = readBoundedCount(buf, total, description);
        List<String> namespaces = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            String namespace = buf.readUtf(MAX_NAMESPACE_LENGTH);
            if (!isValidNamespace(namespace)) {
                throw new DecoderException("Invalid synchronized namespace: " + namespace);
            }
            namespaces.add(namespace);
        }
        return namespaces;
    }

    private static int readBoundedCount(FriendlyByteBuf buf, int maximum, String description) {
        int count = buf.readVarInt();
        if (count < 0 || count > maximum) {
            throw new DecoderException("Invalid " + description + " count: " + count);
        }
        return count;
    }

    private static int encodedStringSize(String value) {
        int bytes = value.getBytes(StandardCharsets.UTF_8).length;
        return varIntSize(bytes) + bytes;
    }

    private static int varIntSize(int value) {
        int bytes = 1;
        while ((value & ~0x7F) != 0) {
            value >>>= 7;
            bytes++;
        }
        return bytes;
    }

    private static final class Chunk {
        private final List<String> genMods = new ArrayList<>();
        private final List<String> disabledMods = new ArrayList<>();
        private int encodedBytes = PACKET_OVERHEAD_BYTES;

        private boolean isEmpty() {
            return genMods.isEmpty() && disabledMods.isEmpty();
        }
    }
}
