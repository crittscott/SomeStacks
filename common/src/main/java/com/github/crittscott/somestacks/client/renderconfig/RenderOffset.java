package com.github.crittscott.somestacks.client.renderconfig;

/** Immutable three-axis render offset, expressed in cell widths. */
public record RenderOffset(float x, float y, float z) {
    public static final RenderOffset ZERO = new RenderOffset(0.0f, 0.0f, 0.0f);
}
