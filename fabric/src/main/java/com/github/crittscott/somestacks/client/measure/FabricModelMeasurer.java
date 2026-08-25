package com.github.crittscott.somestacks.client.measure;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import org.joml.Matrix4f;
import org.joml.Vector4f;

import java.util.List;

/** Measures ordinary quads exposed by Fabric item models after their display transform. */
public final class FabricModelMeasurer implements ModelMeasurement.Backend {
    private static final AABB SAFE_FALLBACK_BOUNDS =
            new AABB(-0.5, -0.5, -0.5, 0.5, 0.5, 0.5);

    @Override
    public ModelMeasurement.Result measure(ItemStack stack) {
        return measure(stack, ItemDisplayContext.FIXED, false);
    }

    @Override
    public ModelMeasurement.Result measureGui(ItemStack stack) {
        return measure(stack, ItemDisplayContext.GUI, true);
    }

    private static ModelMeasurement.Result measure(
            ItemStack stack, ItemDisplayContext context, boolean counterRotate) {
        boolean gui3d = true;
        try {
            BakedModel model = Minecraft.getInstance().getItemRenderer()
                    .getModel(stack, null, null, 0);
            gui3d = model.isGui3d();

            if (model.isCustomRenderer()) {
                return new ModelMeasurement.Result(
                        gui3d, false, SAFE_FALLBACK_BOUNDS, "custom renderer is not measured");
            }

            PoseStack pose = new PoseStack();
            if (counterRotate) {
                pose.mulPose(Axis.YP.rotationDegrees(-45.0f));
                pose.mulPose(Axis.XP.rotationDegrees(-30.0f));
            }
            model.getTransforms().getTransform(context).apply(false, pose);
            pose.translate(-0.5f, -0.5f, -0.5f);
            return measureQuads(model, gui3d, pose);
        } catch (Exception e) {
            return new ModelMeasurement.Result(
                    gui3d, false, null, "measurement threw: " + e);
        }
    }

    private static ModelMeasurement.Result measureQuads(
            BakedModel model, boolean gui3d, PoseStack pose) {
        BoundsCollector collector = new BoundsCollector();
        Matrix4f matrix = pose.last().pose();
        RandomSource random = RandomSource.create();

        for (Direction direction : Direction.values()) {
            random.setSeed(42L);
            collectQuads(model.getQuads(null, direction, random), matrix, collector);
        }
        random.setSeed(42L);
        collectQuads(model.getQuads(null, null, random), matrix, collector);

        if (!collector.hasAny()) {
            // A model with no directly exposed geometry may draw only through Fabric's enhanced
            // item-quad path. ItemRenderer remains safe; the shared 2-D projection would be blank.
            return new ModelMeasurement.Result(
                    gui3d, false, SAFE_FALLBACK_BOUNDS, "model exposes no ordinary quads");
        }
        return new ModelMeasurement.Result(gui3d, true, collector.toAABB(), null);
    }

    private static void collectQuads(
            List<BakedQuad> quads, Matrix4f matrix, BoundsCollector collector) {
        Vector4f vertex = new Vector4f();
        for (BakedQuad quad : quads) {
            int[] data = quad.getVertices();
            for (int i = 0; i < 4; i++) {
                int base = i * 8;
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

    private static final class BoundsCollector {
        private float minX = Float.POSITIVE_INFINITY;
        private float minY = Float.POSITIVE_INFINITY;
        private float minZ = Float.POSITIVE_INFINITY;
        private float maxX = Float.NEGATIVE_INFINITY;
        private float maxY = Float.NEGATIVE_INFINITY;
        private float maxZ = Float.NEGATIVE_INFINITY;
        private boolean any;

        void accept(float x, float y, float z) {
            minX = Math.min(minX, x);
            minY = Math.min(minY, y);
            minZ = Math.min(minZ, z);
            maxX = Math.max(maxX, x);
            maxY = Math.max(maxY, y);
            maxZ = Math.max(maxZ, z);
            any = true;
        }

        boolean hasAny() {
            return any;
        }

        AABB toAABB() {
            return new AABB(minX, minY, minZ, maxX, maxY, maxZ);
        }
    }
}
