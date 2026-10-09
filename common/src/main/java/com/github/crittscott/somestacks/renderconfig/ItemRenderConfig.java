package com.github.crittscott.somestacks.renderconfig;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import javax.annotation.Nullable;
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
        @Nullable RenderOffset offset
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

    /** The JSON vocabulary used by resource, user, and cache override entries. */
    public static final Codec<ItemRenderConfig> CODEC = RecordCodecBuilder.create(instance ->
            instance.group(
                    MODE_CODEC.optionalFieldOf("mode")
                            .forGetter(config -> Optional.ofNullable(config.mode)),
                    SCALE_CODEC.optionalFieldOf("scale")
                            .forGetter(config -> Optional.ofNullable(config.scale)),
                    RenderOffset.CODEC.optionalFieldOf("offset")
                            .forGetter(config -> Optional.ofNullable(config.offset)))
                    .apply(instance, (mode, scale, offset) -> new ItemRenderConfig(
                            mode.orElse(null), scale.orElse(null), offset.orElse(null))));

}
