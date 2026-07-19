package com.github.crittscott.somestacks.client.measure;

import com.github.crittscott.somestacks.client.RenderMode;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.BasePressurePlateBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.ButtonBlock;
import net.minecraft.world.level.block.CarpetBlock;
import net.minecraft.world.phys.AABB;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.Map;

/**
 * Per-item cache of measured render guesses. Profiles are computed lazily on the
 * client thread and invalidated by resource reload or a target-fill change.
 */
public final class AutoRenderProfiles {
    /**
     * Fraction of a stack cell the fitted model should span. Tunable in game via
     * {@code /somestacksdev fill}.
     */
    private static float targetFill = 0.9f;

    // One stack cell (4px) measured in the renderer's local units (8px after the BER's 0.5 scale).
    private static final float CELL_LOCAL_SIZE = 0.5f;
    // Thinnest-to-longest axis ratio below which geometry is treated as a flat card,
    // catching models that report gui3d but draw no real depth.
    private static final float FLAT_RATIO = 0.1f;
    private static final float MIN_EXTENT = 0.001f;
    private static final float MIN_SCALE = 0.01f;
    private static final float MAX_SCALE = 20.0f;
    private static final float BUTTON_FIT_SCALE = 0.75f;

    private static final Map<Item, AutoRenderProfile> CACHE = new HashMap<>();

    private AutoRenderProfiles() {}

    public static AutoRenderProfile get(ItemStack stack) {
        return CACHE.computeIfAbsent(stack.getItem(), item -> compute(stack));
    }

    public static void clear() {
        CACHE.clear();
    }

    public static float getTargetFill() {
        return targetFill;
    }

    public static void setTargetFill(float fill) {
        targetFill = fill;
        CACHE.clear();
    }

    private static AutoRenderProfile compute(ItemStack stack) {
        // Blocks whose art lies in the horizontal plane read as a one-pixel edge when
        // projected flat, so they are presented the way an inventory slot shows them.
        float scaleFactor = fitScaleFactor(stack);

        if (wantsGuiPresentation(stack)) {
            ModelMeasurer.Result guiResult = ModelMeasurer.measureGui(stack);
            AutoRenderProfile fitted = fit(guiResult, RenderMode.GUI, AutoRenderProfile.Source.GUI_MEASURED, scaleFactor);
            if (fitted != null) {
                return fitted;
            }
        }

        ModelMeasurer.Result result = ModelMeasurer.measure(stack);
        AABB bounds = result.bounds();

        if (bounds == null) {
            return new AutoRenderProfile(RenderMode.TWO_D, 1.0f, new float[3],
                    AutoRenderProfile.Source.FAILED, result.customRenderer(), result.gui3d(), null,
                    result.failure() != null ? result.failure() : "no geometry");
        }

        double xSize = bounds.getXsize();
        double ySize = bounds.getYsize();
        double zSize = bounds.getZsize();
        double minExtent = Math.min(xSize, Math.min(ySize, zSize));
        double maxExtent = Math.max(xSize, Math.max(ySize, zSize));

        if (maxExtent < MIN_EXTENT) {
            return new AutoRenderProfile(RenderMode.TWO_D, 1.0f, new float[3],
                    AutoRenderProfile.Source.FAILED, result.customRenderer(), result.gui3d(), bounds,
                    "degenerate bounds");
        }

        // Vanilla's own signal: a generated item sprite reports gui3d false. For custom
        // renderers the flag describes the placeholder model rather than the drawn
        // geometry, so only the measured shape is trusted there.
        boolean flatByModel = !result.customRenderer() && !result.gui3d();
        boolean flatByShape = minExtent / maxExtent < FLAT_RATIO;

        if (flatByModel || flatByShape) {
            return new AutoRenderProfile(RenderMode.TWO_D, 1.0f, new float[3],
                    AutoRenderProfile.Source.FLAT, result.customRenderer(), result.gui3d(), bounds,
                    flatByModel ? "flat: model reports gui3d false" : "flat: thinnest axis under ratio");
        }

        AutoRenderProfile fitted = fit(result, RenderMode.THREE_D,
                result.customRenderer() ? AutoRenderProfile.Source.PROBE_MEASURED : AutoRenderProfile.Source.QUAD_MEASURED,
                scaleFactor);
        return fitted != null ? fitted : new AutoRenderProfile(RenderMode.TWO_D, 1.0f, new float[3],
                AutoRenderProfile.Source.FAILED, result.customRenderer(), result.gui3d(), bounds, "fit failed");
    }

    /**
     * Fits measured bounds to a stack cell. Returns null when the measurement produced
     * nothing usable, leaving the caller to fall back.
     */
    @Nullable
    private static AutoRenderProfile fit(ModelMeasurer.Result result, RenderMode mode, AutoRenderProfile.Source source,
                                         float scaleFactor) {
        AABB bounds = result.bounds();
        if (bounds == null) {
            return null;
        }

        double maxExtent = Math.max(bounds.getXsize(), Math.max(bounds.getYsize(), bounds.getZsize()));
        if (maxExtent < MIN_EXTENT) {
            return null;
        }

        float scale = (float) (targetFill * scaleFactor * CELL_LOCAL_SIZE / maxExtent);
        scale = Math.max(MIN_SCALE, Math.min(MAX_SCALE, scale));

        // The measured bounds already include the renderer's -0.5 shift, so the geometry
        // center directly gives the offset that recenters it on the cell center.
        float[] offset = new float[]{
                (float) (-scale * (bounds.minX + bounds.maxX) * 0.5),
                (float) (-scale * (bounds.minY + bounds.maxY) * 0.5),
                (float) (-scale * (bounds.minZ + bounds.maxZ) * 0.5)
        };

        return new AutoRenderProfile(mode, scale, offset, source,
                result.customRenderer(), result.gui3d(), bounds, null);
    }

    /**
     * Scale applied on top of the cell fit. Fitting sizes every model to the same cell,
     * which reads wrong for block families whose real-world size is much smaller than
     * the things they sit beside.
     */
    private static float fitScaleFactor(ItemStack stack) {
        if (stack.getItem() instanceof BlockItem blockItem
                && blockItem.getBlock() instanceof ButtonBlock) {
            return BUTTON_FIT_SCALE;
        }
        return 1.0f;
    }

    private static boolean wantsGuiPresentation(ItemStack stack) {
        if (!(stack.getItem() instanceof BlockItem blockItem)) {
            return false;
        }
        Block block = blockItem.getBlock();
        return block instanceof BasePressurePlateBlock
                || block instanceof CarpetBlock;
    }
}
