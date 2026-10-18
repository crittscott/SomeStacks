package com.github.crittscott.somestacks.client;

import com.github.crittscott.somestacks.block.BarStackBE;
import com.github.crittscott.somestacks.util.BarCubeIdx;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.data.AtlasIds;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.List;

/**
 * Loader-neutral renderer for a Bar Stack's 64 bars. It ignores the stored item's model
 * entirely: every bar is the same cuboid, drawn with a texture and tint looked up per item in
 * {@link BarTextureStore}, so a Bar Stack looks like bars rather than like stacked ingot items.
 *
 * <p>Bars go into the solid render type in one pass, and their vertices are wound per face so the
 * texture runs along each bar's long axis whichever way its layer lies.
 */
public class BarStackBER implements BlockEntityRenderer<BarStackBE, BarStackBER.BarRenderState> {
    private static final Map<TextureAtlasSprite, UvRegion[]> SPRITE_REGIONS =
            new IdentityHashMap<>();

    static void onResourceReload() {
        SPRITE_REGIONS.clear();
    }

    public BarStackBER(BlockEntityRendererProvider.Context ctx) {
    }

    /** Prepared bars have resolved UVs and tints; submission never reads the level or inventory. */
    public static final class BarRenderState extends BlockEntityRenderState {
        private List<BarRenderData> bars = List.of();
        private int light;
    }

    private record BarRenderData(AABB bounds, int layer, UvRegion[] regions,
                                 BarTextureStore.BarTextureData texture) {}

    @Override
    public BarRenderState createRenderState() {
        return new BarRenderState();
    }

    @Override
    public void extractRenderState(BarStackBE be, BarRenderState state, float partialTick, Vec3 cameraPos,
                                   @Nullable ModelFeatureRenderer.CrumblingOverlay crumblingOverlay) {
        BlockEntityRenderer.super.extractRenderState(be, state, partialTick, cameraPos, crumblingOverlay);
        state.bars = List.of();
        if (be.getLevel() == null) {
            return;
        }
        state.light = LevelRenderer.getLightCoords(be.getLevel(), be.getBlockPos());
        List<BarRenderData> bars = new ArrayList<>();
        for (int idx = 0; idx < BarStackBE.SLOTS; idx++) {
            ItemStack stack = be.getItems().getStackInSlot(idx);
            if (stack.isEmpty()) {
                continue;
            }
            BarTextureStore.BarTextureData texture = BarTextureStore.getTexture(stack);
            TextureAtlasSprite sprite = Minecraft.getInstance()
                    .getAtlasManager().getAtlasOrThrow(AtlasIds.BLOCKS).getSprite(texture.texture());
            bars.add(new BarRenderData(BarCubeIdx.localBox(idx), idx / BarCubeIdx.LAYER_SIZE,
                    regions(sprite), texture));
        }
        state.bars = List.copyOf(bars);
    }

    @Override
    public void submit(BarRenderState state, PoseStack pose, SubmitNodeCollector collector,
                       CameraRenderState camera) {
        List<BarRenderData> bars = state.bars;
        if (bars.isEmpty()) {
            return;
        }
        int light = state.light;
        collector.submitCustomGeometry(pose, RenderTypes.solidMovingBlock(), (snapshot, vc) -> {
            PoseStack barPose = new PoseStack();
            barPose.last().pose().set(snapshot.pose());
            barPose.last().normal().set(snapshot.normal());
            for (BarRenderData bar : bars) {
                AABB bounds = bar.bounds();
                barPose.pushPose();
                barPose.translate(bounds.minX, bounds.minY, bounds.minZ);
                barPose.scale((float) bounds.getXsize(), (float) bounds.getYsize(), (float) bounds.getZsize());
                emitBar(barPose, vc, light, bar.layer(), bar.texture(), bar.regions());
                barPose.popPose();
            }
        });
    }

    private static UvRegion[] regions(TextureAtlasSprite sprite) {
        return SPRITE_REGIONS.computeIfAbsent(sprite, sp -> {
            float u0 = sp.getU0(), v0 = sp.getV0();
            float uRange = sp.getU1() - u0, vRange = sp.getV1() - v0;
            // Top/bottom, long side, and end cap on the sprite's 32-unit grid.
            return new UvRegion[]{
                    region(u0, v0, uRange, vRange, 0f, 0f, 24f, 12f),
                    region(u0, v0, uRange, vRange, 0f, 12f, 24f, 20f),
                    region(u0, v0, uRange, vRange, 0f, 20f, 12f, 28f)};
        });
    }

    private static void emitBar(PoseStack pose, VertexConsumer vc, int light, int layer,
                                BarTextureStore.BarTextureData textureData, UvRegion[] regions) {
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
            // +Y face (top) - reordered vertices to rotate texture 90 degrees
            quad(pose, vc, light, overlay, 0,1,0, 0,1,1, 1,1,1, 1,1,0, top.u0(),top.v0(),top.u1(),top.v1(), 0,1,0, r,g,b,a);
            // -Y face (bottom) - reordered vertices to rotate texture 90 degrees
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
                                   float gridU0, float gridV0, float gridU1, float gridV1) {
        return new UvRegion(
                u0 + uRange * (gridU0 / 32f),
                v0 + vRange * (gridV0 / 32f),
                u0 + uRange * (gridU1 / 32f),
                v0 + vRange * (gridV1 / 32f));
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
