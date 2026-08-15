package com.github.crittscott.somestacks.network;

import com.github.crittscott.somestacks.client.ItemRenderConfig;
import com.github.crittscott.somestacks.client.RenderMode;
import com.github.crittscott.somestacks.util.BlockType;
import io.netty.buffer.Unpooled;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

class PacketCodecTest {

    @Test
    void placeAndDepositRoundTrips() {
        assertRoundTrip(
                new PlaceAndDepositPkt(
                        BlockType.SINGLES_STACK,
                        Direction.NORTH,
                        new BlockPos(12, -4, 91)),
                PlaceAndDepositPkt::encode,
                PlaceAndDepositPkt::decode);
    }

    @Test
    void depositRoundTrips() {
        assertRoundTrip(
                new DepositPkt(new BlockPos(-3, 70, 4), new BlockPos(-3, 69, 4)),
                DepositPkt::encode,
                DepositPkt::decode);
    }

    @Test
    void extractRoundTrips() {
        assertRoundTrip(
                new ExtractPkt(new BlockPos(2, 8, -5), 63),
                ExtractPkt::encode,
                ExtractPkt::decode);
    }

    @Test
    void rotateBlockRoundTrips() {
        assertRoundTrip(
                new RotateBlockPkt(new BlockPos(1, 2, 3)),
                RotateBlockPkt::encode,
                RotateBlockPkt::decode);
    }

    @Test
    void rotateItemRoundTrips() {
        assertRoundTrip(
                new RotateItemPkt(new BlockPos(1, 2, 3), 37),
                RotateItemPkt::encode,
                RotateItemPkt::decode);
    }

    @Test
    void togglePermanentRoundTrips() {
        assertRoundTrip(
                new TogglePermanentPkt(new BlockPos(1, 2, 3)),
                TogglePermanentPkt::encode,
                TogglePermanentPkt::decode);
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
                                        new float[]{0.1f, -0.2f, 0.3f}))),
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
                RenderOverridePkt.set(id, "3d", 1.5f, new float[]{0.0f, 0.25f, -0.5f}),
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
