package com.github.crittscott.somestacks.renderconfig;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;

import java.util.List;

/** Immutable three-axis render offset, expressed in cell widths. */
public record RenderOffset(float x, float y, float z) {
    public static final RenderOffset ZERO = new RenderOffset(0.0f, 0.0f, 0.0f);

    public static final Codec<RenderOffset> CODEC = Codec.floatRange(
                    OverrideJsonCodec.MIN_OFFSET, OverrideJsonCodec.MAX_OFFSET)
            .listOf()
            .comapFlatMap(
                    values -> values.size() == 3
                            ? DataResult.success(new RenderOffset(
                                    values.get(0), values.get(1), values.get(2)))
                            : DataResult.error(() ->
                                    "Expected 3 offset components, found " + values.size()),
                    offset -> List.of(offset.x, offset.y, offset.z));

    public boolean isValid() {
        return OverrideJsonCodec.inRange(x, OverrideJsonCodec.MIN_OFFSET, OverrideJsonCodec.MAX_OFFSET)
                && OverrideJsonCodec.inRange(y, OverrideJsonCodec.MIN_OFFSET, OverrideJsonCodec.MAX_OFFSET)
                && OverrideJsonCodec.inRange(z, OverrideJsonCodec.MIN_OFFSET, OverrideJsonCodec.MAX_OFFSET);
    }
}
