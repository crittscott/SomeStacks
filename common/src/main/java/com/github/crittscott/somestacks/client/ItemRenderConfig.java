package com.github.crittscott.somestacks.client;

import com.github.crittscott.somestacks.util.OverrideJsonCodec;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.handler.codec.DecoderException;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Optional;

/**
 * One override entry in the shared override vocabulary. Every field is optional; an
 * entry owns an item's presentation, so fields it omits take plain defaults (scale 1,
 * zero offset) rather than measured values. An omitted mode is the sole exception and
 * is measured.
 */
public record ItemRenderConfig(
        @Nullable RenderMode mode,
        @Nullable Float scale,
        @Nullable float[] offset
) {
    public static final Codec<RenderMode> MODE_CODEC = Codec.STRING.comapFlatMap(
            id -> {
                RenderMode mode = RenderMode.fromString(id);
                return mode != null
                        ? DataResult.success(mode)
                        : DataResult.error(() -> "Unknown render mode: " + id);
            },
            RenderMode::getId);

    public static final Codec<Float> SCALE_CODEC = Codec.floatRange(
            OverrideJsonCodec.MIN_SCALE, OverrideJsonCodec.MAX_SCALE);

    public static final Codec<float[]> OFFSET_CODEC = Codec.floatRange(
                    OverrideJsonCodec.MIN_OFFSET, OverrideJsonCodec.MAX_OFFSET)
            .listOf()
            .comapFlatMap(
                    values -> values.size() == 3
                            ? DataResult.success(new float[]{
                                    values.get(0), values.get(1), values.get(2)})
                            : DataResult.error(() ->
                                    "Expected 3 offset components, found " + values.size()),
                    values -> List.of(values[0], values[1], values[2]));

    /** The JSON vocabulary used by resource, user, cache, and server override entries. */
    public static final Codec<ItemRenderConfig> CODEC = RecordCodecBuilder.create(instance ->
            instance.group(
                    MODE_CODEC.optionalFieldOf("mode")
                            .forGetter(config -> Optional.ofNullable(config.mode)),
                    SCALE_CODEC.optionalFieldOf("scale")
                            .forGetter(config -> Optional.ofNullable(config.scale)),
                    OFFSET_CODEC.optionalFieldOf("offset")
                            .forGetter(config -> Optional.ofNullable(config.offset)))
                    .apply(instance, (mode, scale, offset) -> new ItemRenderConfig(
                            mode.orElse(null), scale.orElse(null), offset.orElse(null))));

    /** One network vocabulary for override entries, shared by every packet that carries one. */
    public static final StreamCodec<RegistryFriendlyByteBuf, ItemRenderConfig> STREAM_CODEC =
            StreamCodec.of(ItemRenderConfig::encode, ItemRenderConfig::decode);

    private static void encode(RegistryFriendlyByteBuf buf, ItemRenderConfig config) {
        buf.writeBoolean(config.mode != null);
        if (config.mode != null) {
            buf.writeUtf(config.mode.getId());
        }
        buf.writeBoolean(config.scale != null);
        if (config.scale != null) {
            buf.writeFloat(config.scale);
        }
        buf.writeBoolean(config.offset != null);
        if (config.offset != null) {
            buf.writeFloat(config.offset[0]);
            buf.writeFloat(config.offset[1]);
            buf.writeFloat(config.offset[2]);
        }
    }

    private static ItemRenderConfig decode(RegistryFriendlyByteBuf buf) {
        RenderMode mode = null;
        if (buf.readBoolean()) {
            String id = buf.readUtf();
            mode = RenderMode.fromString(id);
            if (mode == null) {
                throw new DecoderException("Invalid render mode: " + id);
            }
        }

        Float scale = buf.readBoolean() ? buf.readFloat() : null;
        if (scale != null && !OverrideJsonCodec.inRange(
                scale, OverrideJsonCodec.MIN_SCALE, OverrideJsonCodec.MAX_SCALE)) {
            throw new DecoderException("Invalid render scale: " + scale);
        }

        float[] offset = buf.readBoolean()
                ? new float[]{buf.readFloat(), buf.readFloat(), buf.readFloat()}
                : null;
        if (offset != null
                && (!OverrideJsonCodec.inRange(
                            offset[0], OverrideJsonCodec.MIN_OFFSET, OverrideJsonCodec.MAX_OFFSET)
                    || !OverrideJsonCodec.inRange(
                            offset[1], OverrideJsonCodec.MIN_OFFSET, OverrideJsonCodec.MAX_OFFSET)
                    || !OverrideJsonCodec.inRange(
                            offset[2], OverrideJsonCodec.MIN_OFFSET, OverrideJsonCodec.MAX_OFFSET))) {
            throw new DecoderException("Invalid render offset");
        }

        return new ItemRenderConfig(mode, scale, offset);
    }
}
