package com.github.crittscott.somestacks.client.measure;

import com.github.crittscott.somestacks.client.RenderMode;
import net.minecraft.world.phys.AABB;

import javax.annotation.Nullable;

/**
 * A guessed render configuration for one item, expressed in the same vocabulary as the
 * authored overrides (mode, scale, offset) so it can be dumped straight into
 * {@code item_render_overrides} JSON. Diagnostics record how the guess was obtained.
 */
public record AutoRenderProfile(
        RenderMode mode,
        float scale,
        float[] offset,
        Source source,
        boolean customRenderer,
        boolean gui3d,
        @Nullable AABB bounds,
        @Nullable String failure
) {
    public enum Source {
        QUAD_MEASURED,
        PROBE_MEASURED,
        GUI_MEASURED,
        FLAT,
        FAILED
    }
}
