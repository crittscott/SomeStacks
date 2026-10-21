package com.github.crittscott.somestacks.client;

import com.github.crittscott.somestacks.block.BarStackBE;
import com.github.crittscott.somestacks.util.BarCubeIdx;
import com.github.crittscott.somestacks.util.SlotAccess;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/**
 * Loader-neutral renderer for a Bar Stack's 64 bars. It ignores the stored item's model
 * entirely: every bar is the same cuboid, drawn with a texture and tint looked up per item in
 * {@link BarTextureStore}, so a Bar Stack looks like bars rather than like stacked ingot items.
 *
 * <p>Bars go into the solid render type in one pass, and their vertices are wound per face so the
 * texture runs along each bar's long axis whichever way its layer lies.
 */
public class BarStackBER implements BlockEntityRenderer<BarStackBE> {
    private static final float TEXTURE_GRID = 32f;
    private static final float[] TOP_BOTTOM_RECT = {0f, 0f, 24f, 12f};
    private static final float[] LONG_SIDE_RECT = {0f, 12f, 24f, 20f};
    private static final float[] END_CAP_RECT = {0f, 20f, 12f, 28f};
    private static final Map<TextureAtlasSprite, UvRegion[]> SPRITE_REGIONS =
            new IdentityHashMap<>();

    private static int resourceGeneration;

    static void onResourceReload() {
        resourceGeneration++;
        SPRITE_REGIONS.clear();
    }

    private record BarRenderData(AABB bounds, int layer, UvRegion[] regions,
                                 BarTextureStore.BarTextureData texture) {}

    /** Prepared bars for one block's contents, valid until its contents or resources change. */
    private record BarCache(int generation, List<BarRenderData> bars) {}

    public BarStackBER(BlockEntityRendererProvider.Context ctx) {
    }

    @Override
    public void render(BarStackBE be, float partialTick, PoseStack pose, MultiBufferSource buffers, int light, int overlay, Vec3 cameraPos) {
        if (be.getLevel() == null) return;

        List<BarRenderData> bars = prepare(be);
        if (bars.isEmpty()) return;
        VertexConsumer vc = buffers.getBuffer(RenderType.solid());

        for (BarRenderData bar : bars) {
            AABB bounds = bar.bounds();

            float sx = (float) bounds.minX;
            float sy = (float) bounds.minY;
            float sz = (float) bounds.minZ;
            float width = (float) bounds.getXsize();
            float height = (float) bounds.getYsize();
            float depth = (float) bounds.getZsize();

            pose.pushPose();
            pose.translate(sx, sy, sz);
            pose.scale(width, height, depth);

            emitBar(pose, vc, bar.regions(), light, bar.layer(), bar.texture());

            pose.popPose();
        }
    }

    /** Resolves textures, sprites, and UVs once per content or resource change. */
    private static List<BarRenderData> prepare(BarStackBE be) {
        if (be.renderCache() instanceof BarCache cache && cache.generation() == resourceGeneration) {
            return cache.bars();
        }
        SlotAccess handler = be.getItems();
        List<BarRenderData> bars = new ArrayList<>();
        for (int idx = 0; idx < BarStackBE.SLOTS; idx++) {
            ItemStack stack = handler.getStackInSlot(idx);
            if (stack.isEmpty()) continue;
            BarTextureStore.BarTextureData textureData = BarTextureStore.getTexture(stack);
            TextureAtlasSprite sprite = Minecraft.getInstance()
                    .getTextureAtlas(TextureAtlas.LOCATION_BLOCKS)
                    .apply(textureData.texture());
            bars.add(new BarRenderData(BarCubeIdx.localBox(idx), idx / BarCubeIdx.LAYER_SIZE,
                    regions(sprite), textureData));
        }
        List<BarRenderData> prepared = List.copyOf(bars);
        be.setRenderCache(new BarCache(resourceGeneration, prepared));
        return prepared;
    }

    private static UvRegion[] regions(TextureAtlasSprite sp) {
        return SPRITE_REGIONS.computeIfAbsent(sp, sprite -> {
            float u0 = sprite.getU0(), v0 = sprite.getV0();
            float uRange = sprite.getU1() - u0, vRange = sprite.getV1() - v0;
            // Top/bottom, long side, and end cap on the sprite's 32-unit grid.
            return new UvRegion[]{
                    region(u0, v0, uRange, vRange, TOP_BOTTOM_RECT),
                    region(u0, v0, uRange, vRange, LONG_SIDE_RECT),
                    region(u0, v0, uRange, vRange, END_CAP_RECT)};
        });
    }

    private static void emitBar(PoseStack pose, VertexConsumer vc, UvRegion[] regions,
                                int light, int layer, BarTextureStore.BarTextureData textureData) {
        int overlay = OverlayTexture.NO_OVERLAY;
        UvRegion top = regions[0], longSide = regions[1], endCap = regions[2];

        // Even layers lay their bars along X (6x2x3), so the Z faces are the long sides and
        // the X faces the end caps. Odd layers lay them along Z (3x2x6) and the two trade places.
        boolean rotated = (layer % 2) == 1;
        UvRegion zFace = rotated ? endCap : longSide;
        UvRegion xFace = rotated ? longSide : endCap;

        int r = textureData.red();
        int g = textureData.green();
        int b = textureData.blue();
        int a = textureData.alpha();

        // +Z face
        quad(pose, vc, light, overlay, 0,0,1, 1,0,1, 1,1,1, 0,1,1, zFace.u0(),zFace.v1(),zFace.u1(),zFace.v0(), 0,0,1, r,g,b,a);
        // -Z face
        quad(pose, vc, light, overlay, 1,0,0, 0,0,0, 0,1,0, 1,1,0, zFace.u0(),zFace.v1(),zFace.u1(),zFace.v0(), 0,0,-1, r,g,b,a);
        // +X face
        quad(pose, vc, light, overlay, 1,0,1, 1,0,0, 1,1,0, 1,1,1, xFace.u0(),xFace.v1(),xFace.u1(),xFace.v0(), 1,0,0, r,g,b,a);
        // -X face
        quad(pose, vc, light, overlay, 0,0,0, 0,0,1, 0,1,1, 0,1,0, xFace.u0(),xFace.v1(),xFace.u1(),xFace.v0(), -1,0,0, r,g,b,a);

        if (rotated) {
            // +Y face (top) - reordered vertices to rotate texture 90°
            quad(pose, vc, light, overlay, 0,1,0, 0,1,1, 1,1,1, 1,1,0, top.u0(),top.v0(),top.u1(),top.v1(), 0,1,0, r,g,b,a);
            // -Y face (bottom) - reordered vertices to rotate texture 90°
            quad(pose, vc, light, overlay, 1,0,0, 1,0,1, 0,0,1, 0,0,0, top.u0(),top.v1(),top.u1(),top.v0(), 0,-1,0, r,g,b,a);
        } else {
            // +Y face (top)
            quad(pose, vc, light, overlay, 0,1,1, 1,1,1, 1,1,0, 0,1,0, top.u0(),top.v1(),top.u1(),top.v0(), 0,1,0, r,g,b,a);
            // -Y face (bottom)
            quad(pose, vc, light, overlay, 0,0,0, 1,0,0, 1,0,1, 0,0,1, top.u0(),top.v1(),top.u1(),top.v0(), 0,-1,0, r,g,b,a);
        }
    }

    /** A sub-rectangle of a sprite, in atlas UV coordinates. */
    private record UvRegion(float u0, float v0, float u1, float v1) {}

    /** Maps a rectangle of the sprite's 32-unit grid onto its place in the atlas. */
    private static UvRegion region(float u0, float v0, float uRange, float vRange,
                                   float[] rectangle) {
        return new UvRegion(
                u0 + uRange * (rectangle[0] / TEXTURE_GRID),
                v0 + vRange * (rectangle[1] / TEXTURE_GRID),
                u0 + uRange * (rectangle[2] / TEXTURE_GRID),
                v0 + vRange * (rectangle[3] / TEXTURE_GRID));
    }

    private static void quad(PoseStack pose, VertexConsumer vc, int light, int overlay,
                             float x1,float y1,float z1, float x2,float y2,float z2,
                             float x3,float y3,float z3, float x4,float y4,float z4,
                             float u0,float v0,float u1,float v1,
                             float nx,float ny,float nz,
                             int r, int g, int b, int a) {
        var last = pose.last();
        var m = last.pose();
        vc.addVertex(m, x1,y1,z1).setColor(r,g,b,a).setUv(u0,v0).setOverlay(overlay).setLight(light).setNormal(last, nx,ny,nz);
        vc.addVertex(m, x2,y2,z2).setColor(r,g,b,a).setUv(u1,v0).setOverlay(overlay).setLight(light).setNormal(last, nx,ny,nz);
        vc.addVertex(m, x3,y3,z3).setColor(r,g,b,a).setUv(u1,v1).setOverlay(overlay).setLight(light).setNormal(last, nx,ny,nz);
        vc.addVertex(m, x4,y4,z4).setColor(r,g,b,a).setUv(u0,v1).setOverlay(overlay).setLight(light).setNormal(last, nx,ny,nz);
    }

}
