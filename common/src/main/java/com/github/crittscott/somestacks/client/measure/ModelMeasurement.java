package com.github.crittscott.somestacks.client.measure;

import com.github.crittscott.somestacks.SomeStacksCommon;
import com.github.crittscott.somestacks.client.ClientRenderPlatform;
import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;

import org.joml.Matrix4f;
import org.joml.Vector4f;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;

import javax.annotation.Nullable;

/**
 * Loader-neutral access to model geometry measurement, and the quad walk and bounds collection both
 * loaders' measurers share. Each loader keeps its own orchestration: how the display transform is
 * applied, when the model's dimensionality is read, and what a custom-renderer item measures as.
 */
public final class ModelMeasurement {
    private static final Set<Item> REPORTED_FAILURES =
            Collections.newSetFromMap(new IdentityHashMap<>());

    private static Backend backend;

    private ModelMeasurement() {}

    /**
     * @param gui3d the resolved baked model's own dimensionality signal
     * @param flatProjectionAvailable whether the shared 2-D renderer can draw the geometry that
     *     produced these bounds
     */
    public record Result(boolean gui3d, boolean flatProjectionAvailable, @Nullable AABB bounds) {}

    /** The loader's measurer, installed once by its client initializer. */
    public interface Backend {
        /** Measures {@code stack} as the {@code 3d} path draws it. */
        Result measure(ItemStack stack);

        /** Measures {@code stack} as the {@code gui} path draws it, counter-rotation included. */
        Result measureGui(ItemStack stack);
    }

    /** Accumulates the axis-aligned bounds of the points it is given. */
    public static final class BoundsCollector {
        private boolean any;
        private float maxX = Float.NEGATIVE_INFINITY;
        private float maxY = Float.NEGATIVE_INFINITY;
        private float maxZ = Float.NEGATIVE_INFINITY;
        private float minX = Float.POSITIVE_INFINITY;
        private float minY = Float.POSITIVE_INFINITY;
        private float minZ = Float.POSITIVE_INFINITY;

        public void accept(float x, float y, float z) {
            minX = Math.min(minX, x);
            minY = Math.min(minY, y);
            minZ = Math.min(minZ, z);
            maxX = Math.max(maxX, x);
            maxY = Math.max(maxY, y);
            maxZ = Math.max(maxZ, z);
            any = true;
        }

        /** The bounds of every point accepted, or null when there were none. */
        @Nullable
        public AABB bounds() {
            return any ? new AABB(minX, minY, minZ, maxX, maxY, maxZ) : null;
        }
    }

    private static void collectQuads(
            List<BakedQuad> quads, Matrix4f matrix, BoundsCollector collector) {
        Vector4f vertex = new Vector4f();
        for (BakedQuad quad : quads) {
            int[] data = quad.getVertices();
            for (int i = 0; i < ClientRenderPlatform.QUAD_VERTICES; i++) {
                int base = i * ClientRenderPlatform.VERTEX_STRIDE;
                vertex.set(
                        Float.intBitsToFloat(data[base]),
                        Float.intBitsToFloat(data[base + 1]),
                        Float.intBitsToFloat(data[base + 2]),
                        1.0f);
                matrix.transform(vertex);
                collector.accept(vertex.x(), vertex.y(), vertex.z());
            }
        }
    }

    /** Measures {@code stack} as the {@code 3d} path draws it, through the installed backend. */
    public static Result measure(ItemStack stack) {
        return backend.measure(stack);
    }

    /** Like {@link #measure}, as the {@code gui} path draws {@code stack}. */
    public static Result measureGui(ItemStack stack) {
        return backend.measureGui(stack);
    }

    /** New models may fail differently after a resource reload. */
    public static void onResourceReload() {
        REPORTED_FAILURES.clear();
    }

    /**
     * The bounds, under {@code pose}, of the quads {@code model} exposes across the loader's render
     * passes for {@code stack} in {@code context}, or null when it exposes none. Each direction
     * group and then the unculled group is read with the seed reset, as ItemRenderer draws them.
     */
    @Nullable
    public static AABB quadBounds(
            ItemStack stack, BakedModel model, ItemDisplayContext context, PoseStack pose) {
        BoundsCollector collector = new BoundsCollector();
        Matrix4f matrix = pose.last().pose();
        RandomSource random = RandomSource.create();

        for (BakedModel pass : ClientRenderPlatform.renderPasses(model, stack, context)) {
            for (Direction direction : Direction.values()) {
                random.setSeed(ClientRenderPlatform.ITEM_MODEL_SEED);
                collectQuads(pass.getQuads(null, direction, random), matrix, collector);
            }
            random.setSeed(ClientRenderPlatform.ITEM_MODEL_SEED);
            collectQuads(pass.getQuads(null, null, random), matrix, collector);
        }
        return collector.bounds();
    }

    /** Reports exceptional model failures once per item until resources are reloaded. */
    public static void reportFailure(
            ItemStack stack, ItemDisplayContext context, Exception exception) {
        if (REPORTED_FAILURES.add(stack.getItem())) {
            SomeStacksCommon.LOGGER.warn(
                    "Item model measurement failed for {} in {}; using fallback presentation",
                    BuiltInRegistries.ITEM.getKey(stack.getItem()),
                    context,
                    exception);
        }
    }

    /** Installs the loader's measurer; each loader's client initializer calls this once. */
    public static void setBackend(Backend backend) {
        ModelMeasurement.backend = backend;
    }
}
