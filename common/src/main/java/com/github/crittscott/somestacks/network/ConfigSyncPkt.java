package com.github.crittscott.somestacks.network;

import com.github.crittscott.somestacks.ServerConfig;
import com.github.crittscott.somestacks.SomeStacksCommon;
import io.netty.handler.codec.DecoderException;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;

/** One byte-bounded chunk of the server settings synchronized to clients. */
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
        List<String> disabledMods) implements CustomPacketPayload {

    public static final Type<ConfigSyncPkt> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(SomeStacksCommon.MODID, "config_sync"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ConfigSyncPkt> STREAM_CODEC =
            StreamCodec.ofMember(ConfigSyncPkt::encode, ConfigSyncPkt::decode);

    private static final int MAX_PACKET_BYTES = 256 * 1024;
    private static final int PACKET_OVERHEAD_BYTES = 64;
    private static final int MAX_TOTAL_NAMESPACES = 4_096;
    private static final int MAX_NAMESPACE_LENGTH = 256;

    private static volatile List<ConfigSyncPkt> currentPackets = List.of();
    private static int nextGeneration;

    public ConfigSyncPkt {
        genMods = List.copyOf(genMods);
        disabledMods = List.copyOf(disabledMods);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    /** Rebuilds the immutable packet sequence after server policy changes. */
    public static synchronized void rebuildCurrent() {
        nextGeneration = nextGeneration == Integer.MAX_VALUE ? 1 : nextGeneration + 1;

        List<String> genMods = boundedNamespaces(ServerConfig.GEN_MODS.get(), "gallery mod");
        List<String> disabledMods = boundedNamespaces(
                ServerConfig.DISABLE_MODS.get(), "disabled mod");

        currentPackets = buildPackets(
                nextGeneration,
                ServerConfig.enableStorageStackBlock(),
                ServerConfig.enableSinglesStackBlock(),
                ServerConfig.enableBarStackBlock(),
                genMods,
                disabledMods);
    }

    /** The packet sequence most recently built at server start or by {@code /ss reload}. */
    public static List<ConfigSyncPkt> currentPackets() {
        return currentPackets;
    }

    private static List<String> boundedNamespaces(List<String> source, String label) {
        LinkedHashSet<String> result = new LinkedHashSet<>();
        for (String entry : source) {
            String namespace = entry.trim().toLowerCase(Locale.ROOT);
            if (namespace.isEmpty() || !result.add(namespace)) {
                continue;
            }
            if (ResourceLocation.tryBuild(namespace, "validation") == null
                    || namespace.length() > MAX_NAMESPACE_LENGTH) {
                result.remove(namespace);
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

    private static void encode(ConfigSyncPkt packet, RegistryFriendlyByteBuf buf) {
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

    private static ConfigSyncPkt decode(RegistryFriendlyByteBuf buf) {
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

    private static void writeNamespaces(RegistryFriendlyByteBuf buf, List<String> namespaces) {
        buf.writeVarInt(namespaces.size());
        for (String namespace : namespaces) {
            buf.writeUtf(namespace, MAX_NAMESPACE_LENGTH);
        }
    }

    private static List<String> readNamespaces(
            RegistryFriendlyByteBuf buf, int total, String description) {
        int count = readBoundedCount(buf, total, description);
        List<String> namespaces = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            String namespace = buf.readUtf(MAX_NAMESPACE_LENGTH);
            if (ResourceLocation.tryBuild(namespace, "validation") == null) {
                throw new DecoderException("Invalid synchronized namespace: " + namespace);
            }
            namespaces.add(namespace);
        }
        return namespaces;
    }

    private static int readBoundedCount(
            RegistryFriendlyByteBuf buf, int maximum, String description) {
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
