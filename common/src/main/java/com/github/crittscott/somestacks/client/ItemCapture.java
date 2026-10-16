package com.github.crittscott.somestacks.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.SubmitNodeCollection;
import net.minecraft.client.renderer.SubmitNodeStorage;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import org.joml.Vector3f;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Resolves an item through vanilla's model pipeline, visits its declared extents, and records its
 * submitted baked quads and layer tints without drawing. Other submitted features identify special
 * geometry that cannot be projected as flat item art. Each result owns its captured state.
 */
public final class ItemCapture {
    /**
     * A baked quad together with its resolved layer tint and the render type the item submitted it
     * with, which binds the atlas holding the quad's sprite. Baked model geometry is immutable.
     */
    public record TintedQuad(BakedQuad quad, int color, RenderType renderType) {}

    private final ItemStackRenderState renderState = new ItemStackRenderState();
    private final List<TintedQuad> quads = new ArrayList<>();
    private boolean specialGeometry;
    private boolean bounded;
    private float minX = Float.POSITIVE_INFINITY, minY = Float.POSITIVE_INFINITY,
            minZ = Float.POSITIVE_INFINITY;
    private float maxX = Float.NEGATIVE_INFINITY, maxY = Float.NEGATIVE_INFINITY,
            maxZ = Float.NEGATIVE_INFINITY;

    private ItemCapture() {}

    /** Captures model extents under the supplied pose, including the item's display transform. */
    public static ItemCapture capture(ItemStack stack, ItemDisplayContext context, @Nullable Level level,
                                      PoseStack pose) {
        return capture(stack, context, level, pose, true);
    }

    /** Captures baked quads and tints without visiting unused bounds. */
    public static ItemCapture captureQuads(ItemStack stack, ItemDisplayContext context,
                                          @Nullable Level level, PoseStack pose) {
        return capture(stack, context, level, pose, false);
    }

    private static ItemCapture capture(ItemStack stack, ItemDisplayContext context,
                                       @Nullable Level level, PoseStack pose, boolean bounds) {
        ItemCapture capture = new ItemCapture();
        Minecraft.getInstance().getItemModelResolver()
                .updateForTopItem(capture.renderState, stack, context, level, null, 0);
        if (capture.renderState.isEmpty()) {
            return capture;
        }
        if (bounds) {
            Vector3f point = new Vector3f();
            capture.renderState.visitExtents(extent -> {
                point.set(extent);
                pose.last().pose().transformPosition(point);
                capture.accept(point.x(), point.y(), point.z());
            });
        }
        CaptureCollector collector = capture.new CaptureCollector();
        capture.renderState.submit(pose, collector, LightTexture.FULL_BRIGHT,
                OverlayTexture.NO_OVERLAY, 0);
        capture.specialGeometry = collector.orders.values().stream()
                .anyMatch(SubmitNodeCollection::wasUsed);
        return capture;
    }

    public boolean isEmpty() {
        return renderState.isEmpty();
    }

    public List<TintedQuad> quads() {
        return List.copyOf(quads);
    }

    public boolean hasSpecialGeometry() {
        return specialGeometry;
    }

    /** The model's declared extents under the capture pose, or null when none were supplied. */
    @Nullable
    public AABB bounds() {
        return bounded ? new AABB(minX, minY, minZ, maxX, maxY, maxZ) : null;
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

    private void acceptQuads(int[] tints, List<BakedQuad> submitted, RenderType renderType) {
        for (BakedQuad quad : submitted) {
            int index = quad.tintIndex();
            int tint = index >= 0 && index < tints.length ? tints[index] : -1;
            quads.add(new TintedQuad(quad, tint, renderType));
        }
    }

    /** Native storage retains special submissions for classification; no features are rendered. */
    private final class CaptureCollector extends SubmitNodeStorage {
        private final Map<Integer, SubmitNodeCollection> orders = new TreeMap<>();

        @Override
        public SubmitNodeCollection order(int order) {
            return orders.computeIfAbsent(order, ignored -> new SubmitNodeCollection(this) {
                @Override
                public void submitItem(PoseStack pose, ItemDisplayContext context, int light,
                                       int overlay, int outline, int[] tints, List<BakedQuad> submitted,
                                       RenderType renderType, ItemStackRenderState.FoilType foil) {
                    acceptQuads(tints, submitted, renderType);
                }
            });
        }

        @Override
        public void submitItem(PoseStack pose, ItemDisplayContext context, int light, int overlay,
                               int outline, int[] tints, List<BakedQuad> submitted,
                               RenderType renderType, ItemStackRenderState.FoilType foil) {
            acceptQuads(tints, submitted, renderType);
        }
    }
}
