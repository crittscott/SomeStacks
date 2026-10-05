package com.github.crittscott.somestacks.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormatElement;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.util.ARGB;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

/**
 * Resolves a stack through vanilla's item model pipeline and records what drawing it would emit,
 * without drawing anything. Baked quads arrive whole, with the tint vanilla resolved for their
 * layer; geometry written vertex by vertex, as a special model renderer or a loader's own item
 * renderer writes it, counts toward the bounds but cannot be reproduced quad by quad.
 *
 * <p>One instance serves the render thread, and each capture replaces the previous one, so a
 * caller must finish with a result before capturing again.
 */
public final class ItemCapture {
    static final int BLOCK_VERTEX_STRIDE =
            DefaultVertexFormat.BLOCK.getVertexSize() / Integer.BYTES;
    static final int BLOCK_POSITION_OFFSET =
            DefaultVertexFormat.BLOCK.getOffset(VertexFormatElement.POSITION) / Integer.BYTES;
    static final int BLOCK_UV_OFFSET =
            DefaultVertexFormat.BLOCK.getOffset(VertexFormatElement.UV0) / Integer.BYTES;

    /** A baked quad together with the color vanilla multiplies into it. */
    public record TintedQuad(BakedQuad quad, int color) {}

    private static final ItemCapture INSTANCE = new ItemCapture();

    private final ItemStackRenderState renderState = new ItemStackRenderState();
    private final List<TintedQuad> quads = new ArrayList<>();
    private final Recorder recorder = new Recorder();
    private final MultiBufferSource buffers = type -> recorder;
    private final Vector3f scratch = new Vector3f();

    private boolean gui3d;
    private boolean vertexGeometry;
    private boolean bounded;
    private float minX, minY, minZ, maxX, maxY, maxZ;

    private ItemCapture() {}

    /**
     * Captures {@code stack} as it would draw in {@code context} under {@code pose}. The pose is
     * left as it was handed in; the display transform and the renderer's -0.5 origin shift that
     * vanilla applies are reflected in {@link #bounds()} but not in the quads, which stay in model
     * space.
     */
    public static ItemCapture capture(ItemStack stack, ItemDisplayContext context, @Nullable Level level,
                                      PoseStack pose) {
        ItemCapture capture = INSTANCE;
        capture.reset();

        // A foil layer writes through a multi-consumer that duplicates every vertex into the glint
        // buffer, which would arrive here as loose vertices. The glint is not geometry.
        ItemStack drawn = stack;
        if (stack.hasFoil()) {
            drawn = stack.copy();
            drawn.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, false);
        }

        Minecraft.getInstance().getItemModelResolver()
                .updateForTopItem(capture.renderState, drawn, context, false, level, null, 0);
        if (capture.renderState.isEmpty()) {
            return capture;
        }
        capture.gui3d = capture.renderState.isGui3d();
        capture.renderState.render(pose, capture.buffers, LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY);
        return capture;
    }

    /** Whether the stack resolved to nothing to draw. */
    public boolean isEmpty() {
        return renderState.isEmpty();
    }

    /** The resolved model's own dimensionality signal. */
    public boolean gui3d() {
        return gui3d;
    }

    /** The baked quads drawn, in drawing order. */
    public List<TintedQuad> quads() {
        return quads;
    }

    /** Whether any geometry was written vertex by vertex rather than as baked quads. */
    public boolean hasVertexGeometry() {
        return vertexGeometry;
    }

    /** The bounds of everything drawn, in the coordinates of the capture's pose, or null if nothing was. */
    @Nullable
    public AABB bounds() {
        return bounded ? new AABB(minX, minY, minZ, maxX, maxY, maxZ) : null;
    }

    private void reset() {
        quads.clear();
        gui3d = true;
        vertexGeometry = false;
        bounded = false;
        minX = minY = minZ = Float.POSITIVE_INFINITY;
        maxX = maxY = maxZ = Float.NEGATIVE_INFINITY;
    }

    private void accept(float x, float y, float z) {
        minX = Math.min(minX, x);
        minY = Math.min(minY, y);
        minZ = Math.min(minZ, z);
        maxX = Math.max(maxX, x);
        maxY = Math.max(maxY, y);
        maxZ = Math.max(maxZ, z);
        bounded = true;
    }

    private void acceptQuad(PoseStack.Pose pose, BakedQuad quad, float r, float g, float b, float a) {
        quads.add(new TintedQuad(quad, color(r, g, b, a)));
        Matrix4f matrix = pose.pose();
        int[] vertices = quad.getVertices();
        for (int i = 0; i < 4; i++) {
            int base = i * BLOCK_VERTEX_STRIDE + BLOCK_POSITION_OFFSET;
            matrix.transformPosition(
                    Float.intBitsToFloat(vertices[base]),
                    Float.intBitsToFloat(vertices[base + 1]),
                    Float.intBitsToFloat(vertices[base + 2]),
                    scratch);
            accept(scratch.x(), scratch.y(), scratch.z());
        }
    }

    private static int color(float r, float g, float b, float a) {
        return ARGB.colorFromFloat(a, r, g, b);
    }

    /** Receives every buffer the render state asks for; it records and never draws. */
    private final class Recorder implements VertexConsumer {
        @Override
        public void putBulkData(PoseStack.Pose pose, BakedQuad quad, float[] brightness,
                                float r, float g, float b, float a, int[] lights, int overlay,
                                boolean readAlpha) {
            acceptQuad(pose, quad, r, g, b, a);
        }

        @Override
        public VertexConsumer addVertex(float x, float y, float z) {
            vertexGeometry = true;
            accept(x, y, z);
            return this;
        }

        @Override
        public VertexConsumer setColor(int r, int g, int b, int a) {
            return this;
        }

        @Override
        public VertexConsumer setUv(float u, float v) {
            return this;
        }

        @Override
        public VertexConsumer setUv1(int u, int v) {
            return this;
        }

        @Override
        public VertexConsumer setUv2(int u, int v) {
            return this;
        }

        @Override
        public VertexConsumer setNormal(float x, float y, float z) {
            return this;
        }
    }
}
