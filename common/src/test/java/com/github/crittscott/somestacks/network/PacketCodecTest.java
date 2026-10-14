package com.github.crittscott.somestacks.network;

import com.github.crittscott.somestacks.client.ClientRenderPacketSink;
import com.github.crittscott.somestacks.client.StackState;
import com.github.crittscott.somestacks.renderconfig.ItemRenderConfig;
import com.github.crittscott.somestacks.renderconfig.RenderMode;
import com.github.crittscott.somestacks.renderconfig.RenderOffset;
import com.github.crittscott.somestacks.util.BlockType;
import com.github.crittscott.somestacks.util.StackMode;
import io.netty.buffer.Unpooled;
import java.util.HashMap;
import java.util.ArrayList;
import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertNull;

class PacketCodecTest {
    private Object previousHandler;
    private Object previousOverrides;
    private final Map<BlockType, Boolean> previousEnablement = new HashMap<>();
    private final RecordingHandler handler = new RecordingHandler();

    private static Field sinkField(String name) throws ReflectiveOperationException {
        Field field = ClientRenderPacketSink.class.getDeclaredField(name);
        field.setAccessible(true);
        return field;
    }

    @BeforeEach
    void isolateClientState() throws ReflectiveOperationException {
        previousHandler = sinkField("handler").get(null);
        previousOverrides = sinkField("serverOverrides").get(null);
        for (BlockType type : BlockType.values()) {
            previousEnablement.put(type, StackState.isBlockTypeEnabled(type));
        }
        ClientRenderPacketSink.setHandler(handler);
        handler.overrides.clear();
    }

    @AfterEach
    void restoreClientState() throws ReflectiveOperationException {
        sinkField("handler").set(null, previousHandler);
        sinkField("serverOverrides").set(null, previousOverrides);
        previousEnablement.forEach(StackState::setBlockEnabled);
    }

    @Test
    void rejectedConfigCountsPreserveEnabledTypesAndRetainedOverrides() {
        ResourceLocation id = new ResourceLocation("test", "retained");
        Map<ResourceLocation, ItemRenderConfig> seed = Map.of(id,
                new ItemRenderConfig(RenderMode.GUI, 1.0f, RenderOffset.ZERO));
        ClientRenderPacketSink.apply(new ConfigSyncPkt(true, false, true, seed));
        handler.overrides.clear();
        for (int count : new int[]{-1, 65537}) {
            ConfigSyncPkt packet = decode(buf -> {
                buf.writeBoolean(false);
                buf.writeBoolean(true);
                buf.writeBoolean(false);
                buf.writeInt(count);
            }, ConfigSyncPkt::decode);
            assertFalse(packet.isValid());
            ClientRenderPacketSink.apply(packet);
            assertTrue(StackState.isBlockTypeEnabled(BlockType.STORAGE_STACK));
            assertFalse(StackState.isBlockTypeEnabled(BlockType.SINGLES_STACK));
            assertTrue(StackState.isBlockTypeEnabled(BlockType.BAR_STACK));
            assertTrue(handler.overrides.isEmpty());
        }
        // Installation exposes retained state without loading a renderer or touching disk.
        RecordingHandler replacement = new RecordingHandler();
        ClientRenderPacketSink.setHandler(replacement);
        assertEquals(List.of(seed), replacement.overrides);
    }

    @Test
    void validEmptyConfigClearsOverridesAndUpdatesEnabledTypes() {
        ClientRenderPacketSink.apply(new ConfigSyncPkt(true, true, true, Map.of(
                new ResourceLocation("test", "seed"), new ItemRenderConfig(RenderMode.GUI, null, null))));
        handler.overrides.clear();
        ConfigSyncPkt packet = decode(buf -> {
            buf.writeBoolean(false);
            buf.writeBoolean(true);
            buf.writeBoolean(false);
            buf.writeInt(0);
        }, ConfigSyncPkt::decode);
        assertTrue(packet.isValid());
        ClientRenderPacketSink.apply(packet);
        assertEquals(List.of(Map.of()), handler.overrides);
        assertFalse(StackState.isBlockTypeEnabled(BlockType.STORAGE_STACK));
        assertTrue(StackState.isBlockTypeEnabled(BlockType.SINGLES_STACK));
        assertFalse(StackState.isBlockTypeEnabled(BlockType.BAR_STACK));
        RecordingHandler replacement = new RecordingHandler();
        ClientRenderPacketSink.setHandler(replacement);
        assertEquals(List.of(Map.of()), replacement.overrides);
    }

    @Test
    void rejectedWriteCountsDoNotBecomeUserLayerWrites() {
        for (int count : new int[]{-1, 4097}) {
            WriteOverridesPkt packet = decode(buf -> buf.writeInt(count), WriteOverridesPkt::decode);
            assertNull(packet.namespaces());
            ClientRenderPacketSink.apply(packet);
            assertTrue(handler.writes.isEmpty());
        }
        WriteOverridesPkt empty = decode(buf -> buf.writeInt(0), WriteOverridesPkt::decode);
        ClientRenderPacketSink.apply(empty);
        assertEquals(List.of(List.of()), handler.writes);
    }

    @Test
    void configWireDecoderSanitizesNumericFieldsBeforeApplication() {
        ResourceLocation id = new ResourceLocation("test", "numeric");
        ConfigSyncPkt packet = decode(buf -> {
            buf.writeBoolean(true);
            buf.writeBoolean(true);
            buf.writeBoolean(true);
            buf.writeInt(1);
            buf.writeResourceLocation(id);
            buf.writeBoolean(true);
            buf.writeUtf("gui");
            buf.writeBoolean(true);
            buf.writeFloat(Float.NaN);
            buf.writeBoolean(true);
            buf.writeFloat(0.0f);
            buf.writeFloat(Float.POSITIVE_INFINITY);
            buf.writeFloat(0.0f);
        }, ConfigSyncPkt::decode);
        assertTrue(packet.isValid());
        ItemRenderConfig sanitized = new ItemRenderConfig(RenderMode.GUI, null, null);
        assertEquals(sanitized, packet.renderOverrides().get(id));
        ClientRenderPacketSink.apply(packet);
        assertEquals(List.of(Map.of(id, sanitized)), handler.overrides);
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

    private static final class RecordingHandler implements ClientRenderPacketSink.Handler {
        final List<Map<ResourceLocation, ItemRenderConfig>> overrides = new ArrayList<>();
        final List<List<String>> writes = new ArrayList<>();

        @Override public void setServerOverrides(Map<ResourceLocation, ItemRenderConfig> value) {
            overrides.add(Map.copyOf(value));
        }

        @Override public void setUserOverride(RenderOverridePkt packet) {
            throw new AssertionError("Unexpected individual override edit");
        }

        @Override public void writeOverrides(List<String> namespaces) {
            writes.add(List.copyOf(namespaces));
        }
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
            FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
            try {
                buf.writeByte(id);
                buf.writeBoolean(true);
                assertThrows(io.netty.handler.codec.DecoderException.class, () -> GestureStatePkt.decode(buf));
            } finally {
                buf.release();
            }
        }
    }

    @Test
    void configSyncRoundTripsOptionalFields() {
        assertRoundTrip(
                new ConfigSyncPkt(
                        true,
                        false,
                        true,
                        Map.of(new ResourceLocation("test", "item"),
                                new ItemRenderConfig(
                                        RenderMode.BLOCK,
                                        2.5f,
                                        new RenderOffset(0.1f, -0.2f, 0.3f)))),
                ConfigSyncPkt::encode,
                ConfigSyncPkt::decode);

        assertRoundTrip(
                new ConfigSyncPkt(
                        false,
                        true,
                        false,
                        Map.of(new ResourceLocation("test", "partial"),
                                new ItemRenderConfig(null, null, null))),
                ConfigSyncPkt::encode,
                ConfigSyncPkt::decode);
    }

    @Test
    void renderOverrideSetAndResetRoundTrip() {
        ResourceLocation id = new ResourceLocation("test", "item");

        assertRoundTrip(
                RenderOverridePkt.set(id, "3d", 1.5f, new RenderOffset(0.0f, 0.25f, -0.5f)),
                RenderOverridePkt::encode,
                RenderOverridePkt::decode);
        assertRoundTrip(
                RenderOverridePkt.reset(id),
                RenderOverridePkt::encode,
                RenderOverridePkt::decode);
    }

    @Test
    void writeOverrideRequestsRoundTrip() {
        assertRoundTrip(
                WriteOverridesPkt.userLayer(),
                WriteOverridesPkt::encode,
                WriteOverridesPkt::decode);
        assertRoundTrip(
                WriteOverridesPkt.dump(List.of("minecraft", "somestacks")),
                WriteOverridesPkt::encode,
                WriteOverridesPkt::decode);
    }

    @Test
    void configSyncOwnsAnImmutableOverrideSnapshot() {
        ResourceLocation id = new ResourceLocation("test", "item");
        ItemRenderConfig config = new ItemRenderConfig(RenderMode.GUI, 1.0f, RenderOffset.ZERO);
        Map<ResourceLocation, ItemRenderConfig> authored = new HashMap<>();
        authored.put(id, config);
        ConfigSyncPkt packet = new ConfigSyncPkt(true, true, true, authored);
        authored.clear();
        assertEquals(Map.of(id, config), packet.renderOverrides());
        assertThrows(UnsupportedOperationException.class, () -> packet.renderOverrides().clear());
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
