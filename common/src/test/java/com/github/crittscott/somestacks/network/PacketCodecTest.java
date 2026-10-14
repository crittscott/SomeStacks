package com.github.crittscott.somestacks.network;

import com.github.crittscott.somestacks.ServerConfig;
import com.github.crittscott.somestacks.client.ClientRenderPacketSink;
import com.github.crittscott.somestacks.client.ClientRenderToolState;
import com.github.crittscott.somestacks.client.StackState;
import com.github.crittscott.somestacks.util.BlockType;
import com.github.crittscott.somestacks.util.StackMode;
import io.netty.buffer.Unpooled;
import io.netty.handler.codec.DecoderException;
import net.minecraft.network.FriendlyByteBuf;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PacketCodecTest {
    private List<String> previousGallery;
    private Set<String> previousDenied;
    private boolean[] previousFlags;

    @BeforeEach
    void isolateClientState() {
        previousGallery = ClientRenderToolState.genMods();
        previousDenied = ClientRenderToolState.disabledMods();
        previousFlags = flags();
        ClientRenderPacketSink.clear();
    }

    @AfterEach
    void restoreClientState() {
        ClientRenderPacketSink.clear();
        ClientRenderToolState.replace(previousGallery, List.copyOf(previousDenied));
        StackState.setBlockEnabled(BlockType.STORAGE_STACK, previousFlags[0]);
        StackState.setBlockEnabled(BlockType.SINGLES_STACK, previousFlags[1]);
        StackState.setBlockEnabled(BlockType.BAR_STACK, previousFlags[2]);
    }

    /**
     * Complete snapshots publish flags and both lists only at their final chunk. To reproduce
     * in-game: configure disabled types and namespace lists, join, and inspect mode cycling and
     * /ss write list. A large list exercises multiple packets; exact atomicity requires this test.
     */
    @Test
    void configSnapshotsRoundTripAndPublishAtomically() {
        for (int size : new int[] {0, 1, 4096}) {
            List<String> gallery = new ArrayList<>();
            List<String> denied = new ArrayList<>();
            for (int i = 0; i < size; i++) {
                gallery.add("gallery" + i + "a".repeat(240));
                denied.add("denied" + i + "b".repeat(240));
            }
            boolean[] expectedFlags = {size == 4096, size != 4096, size == 0};
            ServerConfig.Settings defaults = ServerConfig.Settings.defaults();
            List<ConfigSyncPkt> packets = ConfigSyncPkt.snapshot(new ServerConfig.Settings(
                    defaults.maxPileHeight(), expectedFlags[0], expectedFlags[1], expectedFlags[2],
                    denied, defaults.disableItems(), defaults.renderGalleryPlacementsPerTick(),
                    defaults.galleryEnabled(), defaults.galleryPermissionLevel(), gallery,
                    defaults.genItems()));
            assertTrue(size == 4096 ? packets.size() > 1 : packets.size() == 1, "Chunking");
            List<String> beforeGallery = ClientRenderToolState.genMods();
            Set<String> beforeDenied = ClientRenderToolState.disabledMods();
            boolean[] beforeFlags = flags();
            int bytes = 0;
            for (int i = 0; i < packets.size(); i++) {
                ConfigSyncPkt packet = packets.get(i);
                assertEquals(i == 0, packet.first(), "First marker");
                assertEquals(i == packets.size() - 1, packet.last(), "Last marker");
                assertEquals(packets.get(0).generation(), packet.generation(), "Generation changed");
                bytes += packet.budgetBytes();
                byte[] wire = encode(packet, ConfigSyncPkt::encode);
                assertTrue(wire.length <= ConfigSyncPkt.MAX_PACKET_BYTES, "Encoded chunk too large");
                ConfigSyncPkt decoded = ConfigSyncPkt.decode(new FriendlyByteBuf(Unpooled.wrappedBuffer(wire)));
                assertEquals(packet, decoded, "Chunk codec round trip");
                ClientRenderPacketSink.apply(decoded);
                if (!packet.last()) {
                    assertEquals(beforeGallery, ClientRenderToolState.genMods(), "Gallery published early");
                    assertEquals(beforeDenied, ClientRenderToolState.disabledMods(), "Deny list published early");
                    assertArrayEquals(beforeFlags, flags(), "Flags published early");
                }
            }
            assertTrue(packets.size() <= ConfigSyncPkt.MAX_GENERATION_CHUNKS, "Chunk budget");
            assertTrue(bytes <= ConfigSyncPkt.MAX_GENERATION_BYTES, "Generation byte budget");
            assertEquals(gallery, ClientRenderToolState.genMods(), "Published gallery list");
            assertEquals(Set.copyOf(denied), ClientRenderToolState.disabledMods(), "Published deny set");
            assertArrayEquals(expectedFlags, flags(), "Published flags");
        }
    }

    /** Missing, inconsistent, and superseded generations cannot publish partial settings. */
    @Test
    void configStagingChecksSequencesAndTotals() {
        ClientRenderToolState.replace(List.of(), List.of());
        setFlags(true, true, true);
        assertThrows(RuntimeException.class, () -> ClientRenderPacketSink.apply(chunk(1, false, true, 1, "tail")));
        ClientRenderPacketSink.apply(chunk(2, true, false, 2, "old"));
        assertEquals(List.of(), ClientRenderToolState.genMods(), "Missing last published");
        ClientRenderPacketSink.apply(chunk(3, true, true, 1, "replacement"));
        assertEquals(List.of("replacement"), ClientRenderToolState.genMods(), "New first did not replace staging");
        assertThrows(RuntimeException.class, () -> ClientRenderPacketSink.apply(chunk(2, false, true, 2, "old_tail")));
        ClientRenderPacketSink.apply(chunk(4, true, false, 2, "first"));
        assertThrows(RuntimeException.class, () -> ClientRenderPacketSink.apply(chunk(5, false, true, 2, "tail")));
        for (ConfigSyncPkt changed : List.of(
                new ConfigSyncPkt(6, false, true, false, true, true, 2, 0, List.of("tail"), List.of()),
                new ConfigSyncPkt(6, false, true, true, false, true, 2, 0, List.of("tail"), List.of()),
                new ConfigSyncPkt(6, false, true, true, true, false, 2, 0, List.of("tail"), List.of()),
                chunk(6, false, true, 3, "tail"),
                new ConfigSyncPkt(6, false, true, true, true, true, 2, 1, List.of("tail"), List.of()))) {
            ClientRenderPacketSink.apply(chunk(6, true, false, 2, "first"));
            assertThrows(RuntimeException.class, () -> ClientRenderPacketSink.apply(changed));
            assertThrows(RuntimeException.class, () -> ClientRenderPacketSink.apply(chunk(6, false, true, 2, "tail")));
        }
        assertThrows(RuntimeException.class, () -> ClientRenderPacketSink.apply(chunk(7, true, true, 2, "only")));
        ClientRenderPacketSink.apply(chunk(8, true, false, 1, "first"));
        assertThrows(RuntimeException.class, () -> ClientRenderPacketSink.apply(chunk(8, false, true, 1, "extra")));
        ClientRenderPacketSink.apply(new ConfigSyncPkt(9, true, false, true, true, true,
                0, 2, List.of(), List.of("denied")));
        assertThrows(RuntimeException.class, () -> ClientRenderPacketSink.apply(new ConfigSyncPkt(
                9, false, true, true, true, true, 0, 2, List.of(), List.of("denied"))));
        assertEquals(List.of("replacement"), ClientRenderToolState.genMods(), "Bad sequence replaced published state");
        assertArrayEquals(new boolean[] {true, true, true}, flags(), "Bad sequence changed flags");
        assertTrue(ClientRenderToolState.disabledMods().isEmpty(), "Bad sequence changed deny set");
        ClientRenderPacketSink.apply(chunk(10, true, true, 1, "recovered"));
        assertEquals(List.of("recovered"), ClientRenderToolState.genMods(), "Valid snapshot after rejection failed");
    }

    /** Malicious chunk sequences clear staging without publishing an incomplete snapshot. */
    @Test
    void configStagingRejectsAndClearsInvalidGenerations() {
        List<String> published = ClientRenderToolState.genMods();
        ClientRenderPacketSink.apply(chunk(1, true, false, 10, "first"));
        assertEquals(published, ClientRenderToolState.genMods(), "Incomplete snapshot published");
        assertThrows(RuntimeException.class, () -> ClientRenderPacketSink.apply(chunk(1, false, false, 10)));
        assertThrows(RuntimeException.class, () -> ClientRenderPacketSink.apply(chunk(1, false, true, 10, "second")));
        ClientRenderPacketSink.apply(chunk(2, true, false, 10, "first"));
        assertThrows(RuntimeException.class, () -> ClientRenderPacketSink.apply(chunk(2, false, true, 10, "first")));
        for (int i = 0; i < ConfigSyncPkt.MAX_GENERATION_CHUNKS; i++) {
            ClientRenderPacketSink.apply(chunk(3, i == 0, false, 4096, "mod" + i));
        }
        assertThrows(RuntimeException.class, () -> ClientRenderPacketSink.apply(chunk(3, false, false, 4096, "overflow")));
        assertThrows(RuntimeException.class, () -> ClientRenderPacketSink.apply(chunk(3, false, true, 4096, "tail")));
        assertEquals(published, ClientRenderToolState.genMods(), "Rejected snapshot published");
    }

    /** Invalid wire counts, generations, and namespace encodings fail during decoding. */
    @Test
    void configDecoderRejectsMalformedCountsAndNamespaces() {
        for (int[] counts : new int[][] {{-1, 0, 0, 0}, {4097, 0, 0, 0},
                {0, -1, 0, 0}, {0, 4097, 0, 0}, {1, 0, -1, 0}, {1, 0, 2, 0},
                {0, 1, 0, -1}, {0, 1, 0, 2}}) {
            assertDecodeFails(buf -> {
                writeHeader(buf, 1, counts[0], counts[1]);
                buf.writeVarInt(counts[2]);
                buf.writeVarInt(counts[3]);
            });
        }
        for (int generation : new int[] {0, -1}) {
            assertDecodeFails(buf -> {
                writeHeader(buf, generation, 0, 0);
                buf.writeVarInt(0);
                buf.writeVarInt(0);
            });
        }
        for (String namespace : List.of("", "Uppercase", "bad namespace", "a".repeat(257))) {
            assertDecodeFails(buf -> {
                writeHeader(buf, 1, 1, 0);
                buf.writeVarInt(1);
                buf.writeUtf(namespace);
                buf.writeVarInt(0);
            });
        }
    }

    /** An oversized server payload is rejected before decoding reads it. */
    @Test
    void configDecoderEnforcesPacketBudget() {
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        try {
            buf.writeZero(ConfigSyncPkt.MAX_PACKET_BYTES + 1);
            assertThrows(DecoderException.class, () -> ConfigSyncPkt.decode(buf));
            assertEquals(0, buf.readerIndex(), "Oversized packet was read before rejection");
        } finally {
            buf.release();
        }
        assertRoundTrip(new ConfigSyncPkt(1, true, true, true, false, true,
                        2, 1, List.of("minecraft", "somestacks"), List.of("denied")),
                ConfigSyncPkt::encode, ConfigSyncPkt::decode);
    }

    @Test
    void gestureStateRoundTripsEveryModeAndModifier() {
        for (StackMode mode : StackMode.values()) {
            for (boolean down : new boolean[] {false, true}) {
                assertRoundTrip(new GestureStatePkt(mode, down), GestureStatePkt::encode, GestureStatePkt::decode);
            }
        }
    }

    @Test
    void gestureStateRejectsInvalidModes() {
        for (int id : new int[] {StackMode.values().length, 255}) {
            assertThrows(DecoderException.class, () -> decode(buf -> {
                buf.writeByte(id);
                buf.writeBoolean(true);
            }, GestureStatePkt::decode));
        }
    }

    private static ConfigSyncPkt chunk(int generation, boolean first, boolean last, int total,
                                       String... namespaces) {
        return new ConfigSyncPkt(generation, first, last, true, true, true,
                total, 0, List.of(namespaces), List.of());
    }

    private static void writeHeader(FriendlyByteBuf buf, int generation, int gallery, int denied) {
        buf.writeVarInt(generation);
        for (int i = 0; i < 5; i++) buf.writeBoolean(true);
        buf.writeVarInt(gallery);
        buf.writeVarInt(denied);
    }

    private static void assertDecodeFails(Consumer<FriendlyByteBuf> writer) {
        assertThrows(RuntimeException.class, () -> decode(writer, ConfigSyncPkt::decode));
    }

    private static boolean[] flags() {
        return new boolean[] {StackState.isBlockTypeEnabled(BlockType.STORAGE_STACK),
                StackState.isBlockTypeEnabled(BlockType.SINGLES_STACK),
                StackState.isBlockTypeEnabled(BlockType.BAR_STACK)};
    }

    private static void setFlags(boolean storage, boolean singles, boolean bar) {
        StackState.setBlockEnabled(BlockType.STORAGE_STACK, storage);
        StackState.setBlockEnabled(BlockType.SINGLES_STACK, singles);
        StackState.setBlockEnabled(BlockType.BAR_STACK, bar);
    }

    private static <T> T decode(Consumer<FriendlyByteBuf> writer,
                                Function<FriendlyByteBuf, T> decoder) {
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            writer.accept(buffer);
            return decoder.apply(buffer);
        } finally {
            buffer.release();
        }
    }

    private static <T> void assertRoundTrip(
            T message,
            BiConsumer<T, FriendlyByteBuf> encoder,
            Function<FriendlyByteBuf, T> decoder) {
        byte[] first = encode(message, encoder);
        T decoded = decoder.apply(new FriendlyByteBuf(Unpooled.wrappedBuffer(first)));
        byte[] second = encode(decoded, encoder);

        assertArrayEquals(first, second);
    }

    private static <T> byte[] encode(T message, BiConsumer<T, FriendlyByteBuf> encoder) {
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        encoder.accept(message, buffer);
        byte[] bytes = new byte[buffer.readableBytes()];
        buffer.getBytes(0, bytes);
        return bytes;
    }
}
