package com.github.crittscott.somestacks.client;

import javax.annotation.Nullable;

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
) {}
