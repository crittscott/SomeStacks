package com.github.crittscott.somestacks.client.measure;

import com.github.crittscott.somestacks.client.CubeRenderHelper;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.client.ForgeHooksClient;
import net.minecraftforge.client.extensions.common.IClientItemExtensions;
import org.joml.Matrix4f;
import org.joml.Vector4f;

import javax.annotation.Nonnull;
import java.util.List;

/**
 * Measures the geometry an item will actually produce in the FIXED display context,
 * mirroring the vanilla/Forge {@code ItemRenderer.render} pipeline: item-override
 * resolution, {@code applyTransform} (which may substitute another model), the
 * renderer's -0.5 origin shift, and all Forge render passes. Custom-renderer (BEWLR)
 * items are probed by running their renderer once against a vertex-capturing buffer.
 */
public final class ModelMeasurer implements ModelMeasurement.Backend {

    /** Measures as the {@code 3d} path draws: the FIXED display context. */
    @Override
    public ModelMeasurement.Result measure(ItemStack stack) {
        return measure(stack, ItemDisplayContext.FIXED, false);
    }

    /**
     * Measures as the {@code gui} path draws: the GUI display context behind the same
     * counter-rotation that path applies, so the fit accounts for the tilted presentation.
     */
    @Override
    public ModelMeasurement.Result measureGui(ItemStack stack) {
        return measure(stack, ItemDisplayContext.GUI, true);
    }

    private static ModelMeasurement.Result measure(
            ItemStack stack, ItemDisplayContext context, boolean counterRotate) {
        boolean gui3d = true;
        try {
            BakedModel model = Minecraft.getInstance().getItemRenderer().getModel(stack, null, null, 0);

            PoseStack pose = new PoseStack();
            pose.pushPose();
            if (counterRotate) {
                pose.mulPose(Axis.YP.rotationDegrees(-45.0f));
                pose.mulPose(Axis.XP.rotationDegrees(-30.0f));
            }
            model = ForgeHooksClient.handleCameraTransforms(pose, model, context, false);
            pose.translate(-0.5f, -0.5f, -0.5f);
            // Read the flag off the substituted model, which is the one that gets drawn.
            gui3d = model.isGui3d();

            if (model.isCustomRenderer()) {
                return probeCustomRenderer(stack, gui3d, context, pose);
            }
            return measureQuads(stack, model, gui3d, context, pose);
        } catch (Exception e) {
            return new ModelMeasurement.Result(gui3d, false, null, "measurement threw: " + e);
        }
    }

    private static ModelMeasurement.Result measureQuads(ItemStack stack, BakedModel model, boolean gui3d,
                                       ItemDisplayContext context, PoseStack pose) {
        BoundsCollector collector = new BoundsCollector();
        Matrix4f matrix = pose.last().pose();
        RandomSource random = RandomSource.create();

        for (BakedModel pass : model.getRenderPasses(stack, CubeRenderHelper.fabulousFlag(stack, context))) {
            for (Direction direction : Direction.values()) {
                // Seed 42 per group matches ItemRenderer.renderModelLists.
                random.setSeed(42L);
                collectQuads(pass.getQuads(null, direction, random), matrix, collector);
            }
            random.setSeed(42L);
            collectQuads(pass.getQuads(null, null, random), matrix, collector);
        }

        if (!collector.hasAny()) {
            return new ModelMeasurement.Result(gui3d, false, null, "model has no quads");
        }
        return new ModelMeasurement.Result(gui3d, true, collector.toAABB(), null);
    }

    private static void collectQuads(List<BakedQuad> quads, Matrix4f matrix, BoundsCollector collector) {
        Vector4f v = new Vector4f();
        for (BakedQuad quad : quads) {
            int[] data = quad.getVertices();
            for (int i = 0; i < 4; i++) {
                int base = i * 8;
                v.set(Float.intBitsToFloat(data[base]),
                        Float.intBitsToFloat(data[base + 1]),
                        Float.intBitsToFloat(data[base + 2]),
                        1.0f);
                matrix.transform(v);
                collector.accept(v.x(), v.y(), v.z());
            }
        }
    }

    private static ModelMeasurement.Result probeCustomRenderer(
            ItemStack stack, boolean gui3d, ItemDisplayContext context, PoseStack pose) {
        BoundsCollector collector = new BoundsCollector();
        CapturingBufferSource buffers = new CapturingBufferSource(collector);
        try {
            IClientItemExtensions.of(stack).getCustomRenderer().renderByItem(
                    stack, context, pose, buffers,
                    LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY);
        } catch (Exception e) {
            return new ModelMeasurement.Result(gui3d, false, null, "custom renderer threw: " + e);
        }
        if (!collector.hasAny()) {
            return new ModelMeasurement.Result(gui3d, false, null, "custom renderer emitted no vertices");
        }
        return new ModelMeasurement.Result(gui3d, false, collector.toAABB(), null);
    }

    private static final class BoundsCollector {
        private float minX = Float.POSITIVE_INFINITY;
        private float minY = Float.POSITIVE_INFINITY;
        private float minZ = Float.POSITIVE_INFINITY;
        private float maxX = Float.NEGATIVE_INFINITY;
        private float maxY = Float.NEGATIVE_INFINITY;
        private float maxZ = Float.NEGATIVE_INFINITY;
        private boolean any = false;

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

    private static final class CapturingBufferSource implements MultiBufferSource {
        private final CapturingConsumer consumer;

        CapturingBufferSource(BoundsCollector collector) {
            this.consumer = new CapturingConsumer(collector);
        }

        @Nonnull
        @Override
        public VertexConsumer getBuffer(@Nonnull RenderType type) {
            return consumer;
        }
    }

    private static final class CapturingConsumer implements VertexConsumer {
        private final BoundsCollector collector;

        CapturingConsumer(BoundsCollector collector) {
            this.collector = collector;
        }

        @Override
        public VertexConsumer vertex(double x, double y, double z) {
            collector.accept((float) x, (float) y, (float) z);
            return this;
        }

        @Override
        public VertexConsumer color(int r, int g, int b, int a) {
            return this;
        }

        @Override
        public VertexConsumer uv(float u, float v) {
            return this;
        }

        @Override
        public VertexConsumer overlayCoords(int u, int v) {
            return this;
        }

        @Override
        public VertexConsumer uv2(int u, int v) {
            return this;
        }

        @Override
        public VertexConsumer normal(float x, float y, float z) {
            return this;
        }

        @Override
        public void endVertex() {
        }

        @Override
        public void defaultColor(int r, int g, int b, int a) {
        }

        @Override
        public void unsetDefaultColor() {
        }
    }
}
