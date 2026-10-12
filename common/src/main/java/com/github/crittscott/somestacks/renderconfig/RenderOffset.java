package com.github.crittscott.somestacks.renderconfig;

/** Immutable three-axis render offset, expressed in cell widths. */
public record RenderOffset(float x, float y, float z) {
    public static final RenderOffset ZERO = new RenderOffset(0.0f, 0.0f, 0.0f);

    public boolean isValid() {
        return OverrideJsonCodec.inRange(x, OverrideJsonCodec.MIN_OFFSET, OverrideJsonCodec.MAX_OFFSET)
                && OverrideJsonCodec.inRange(y, OverrideJsonCodec.MIN_OFFSET, OverrideJsonCodec.MAX_OFFSET)
                && OverrideJsonCodec.inRange(z, OverrideJsonCodec.MIN_OFFSET, OverrideJsonCodec.MAX_OFFSET);
    }
}
