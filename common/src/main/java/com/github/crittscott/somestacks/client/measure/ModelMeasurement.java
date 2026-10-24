package com.github.crittscott.somestacks.client.measure;

import com.github.crittscott.somestacks.client.ItemCapture;
import com.github.crittscott.somestacks.client.CubeRenderHelper;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;

import javax.annotation.Nullable;

/**
 * Measures the geometry an item actually draws, by capturing a vanilla draw of it: the item model
 * the stack resolves to, its display transform, the renderer's -0.5 origin shift, every layer, and
 * whatever a special model renderer emits.
 */
public final class ModelMeasurement {
    private ModelMeasurement() {}

    /**
     * @param gui3d the resolved item model's own dimensionality signal
     * @param flatProjectionAvailable whether the shared 2-D renderer can draw the geometry that
     *                                produced these bounds
     */
    public record Result(
            boolean gui3d, boolean flatProjectionAvailable,
            @Nullable AABB bounds, @Nullable String failure) {}

    /** Measures as the {@code 3d} path draws: the FIXED display context. */
    public static Result measure(ItemStack stack) {
        return measure(stack, ItemDisplayContext.FIXED, false);
    }

    /**
     * Measures as the {@code gui} path draws: the GUI display context behind the same
     * counter-rotation that path applies, so the fit accounts for the tilted presentation.
     */
    public static Result measureGui(ItemStack stack) {
        return measure(stack, ItemDisplayContext.GUI, true);
    }

    private static Result measure(ItemStack stack, ItemDisplayContext context, boolean counterRotate) {
        try {
            PoseStack pose = new PoseStack();
            if (counterRotate) {
                CubeRenderHelper.applyGuiCounterRotation(pose);
            }
            ItemCapture capture = ItemCapture.capture(stack, context, null, pose);
            if (capture.isEmpty()) {
                return new Result(true, false, null, "item resolves to no model");
            }
            AABB bounds = capture.bounds();
            if (bounds == null) {
                return new Result(capture.gui3d(), false, null, "item draws nothing");
            }
            // Only baked quads can be laid onto a cube face; anything drawn vertex by vertex
            // stays on the 3-D path.
            boolean flat = !capture.hasVertexGeometry() && !capture.quads().isEmpty();
            return new Result(capture.gui3d(), flat, bounds, null);
        } catch (Exception e) {
            return new Result(true, false, null, "measurement threw: " + e);
        }
    }
}
