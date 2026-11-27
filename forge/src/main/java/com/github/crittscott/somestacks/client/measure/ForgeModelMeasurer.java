package com.github.crittscott.somestacks.client.measure;

import com.github.crittscott.somestacks.client.ClientRenderPlatform;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.client.ForgeHooksClient;
import net.minecraftforge.client.extensions.common.IClientItemExtensions;

import javax.annotation.Nonnull;

/**
 * Measures the geometry an item will actually produce in the FIXED display context, mirroring the
 * vanilla/Forge {@code ItemRenderer.render} pipeline: item-override resolution, {@code
 * applyTransform} (which may substitute another model), the renderer's -0.5 origin shift, and all
 * Forge render passes. Custom-renderer (BEWLR) items are probed by running their renderer once
 * against a vertex-capturing buffer.
 */
public final class ForgeModelMeasurer implements ModelMeasurement.Backend {

    /** Measures as the {@code 3d} path draws: the FIXED display context. */
    @Override
    public ModelMeasurement.Result measure(ItemStack stack) {
        return measure(stack, ItemDisplayContext.FIXED, false);
    }

    /**
     * Measures as the {@code gui} path draws: the GUI display context behind {@link
     * ClientRenderPlatform#applyGuiCounterRotation}, so the fit accounts for the presentation.
     */
    @Override
    public ModelMeasurement.Result measureGui(ItemStack stack) {
        return measure(stack, ItemDisplayContext.GUI, true);
    }

    private static ModelMeasurement.Result measure(
            ItemStack stack, ItemDisplayContext context, boolean counterRotate) {
        boolean gui3d = true;
        try {
            BakedModel model =
                    Minecraft.getInstance().getItemRenderer().getModel(stack, null, null, 0);

            PoseStack pose = new PoseStack();
            pose.pushPose();
            if (counterRotate) {
                ClientRenderPlatform.applyGuiCounterRotation(pose);
            }
            model = ForgeHooksClient.handleCameraTransforms(pose, model, context, false);
            pose.translate(-0.5f, -0.5f, -0.5f);
            // Read the flag off the substituted model, which is the one that gets drawn.
            gui3d = model.isGui3d();

            if (model.isCustomRenderer()) {
                return probeCustomRenderer(stack, gui3d, context, pose);
            }
            AABB bounds = ModelMeasurement.quadBounds(stack, model, context, pose);
            return new ModelMeasurement.Result(gui3d, bounds != null, bounds);
        } catch (Exception e) {
            ModelMeasurement.reportFailure(stack, context, e);
            return new ModelMeasurement.Result(gui3d, false, null);
        }
    }

    /** Runs the item's custom renderer once and measures the vertices it emits. */
    private static ModelMeasurement.Result probeCustomRenderer(
            ItemStack stack, boolean gui3d, ItemDisplayContext context, PoseStack pose) {
        ModelMeasurement.BoundsCollector collector = new ModelMeasurement.BoundsCollector();
        IClientItemExtensions.of(stack)
                .getCustomRenderer()
                .renderByItem(
                        stack,
                        context,
                        pose,
                        new CapturingBufferSource(collector),
                        LightTexture.FULL_BRIGHT,
                        OverlayTexture.NO_OVERLAY);
        return new ModelMeasurement.Result(gui3d, false, collector.bounds());
    }

    private static final class CapturingBufferSource implements MultiBufferSource {
        private final CapturingConsumer consumer;

        CapturingBufferSource(ModelMeasurement.BoundsCollector collector) {
            this.consumer = new CapturingConsumer(collector);
        }

        @Nonnull
        @Override
        public VertexConsumer getBuffer(@Nonnull RenderType type) {
            return consumer;
        }
    }

    private static final class CapturingConsumer implements VertexConsumer {
        private final ModelMeasurement.BoundsCollector collector;

        CapturingConsumer(ModelMeasurement.BoundsCollector collector) {
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
        public void endVertex() {}

        @Override
        public void defaultColor(int r, int g, int b, int a) {}

        @Override
        public void unsetDefaultColor() {}
    }
}
