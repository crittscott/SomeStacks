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
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.AABB;
import org.joml.Matrix4f;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.item.ItemStack;

import java.util.HashMap;
import java.util.IdentityHashMap;
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
    private static final Map<TextureAtlasSprite, UvRegion[]> SPRITE_REGIONS = new IdentityHashMap<>();

    private static final Map<ResourceLocation, TextureAtlasSprite> SPRITES = new HashMap<>();

    /** Releases atlas coordinates and sprites when resource textures change. */
    public static void onResourceReload() {
        SPRITE_REGIONS.clear();
        SPRITES.clear();
    }

    public BarStackBER(BlockEntityRendererProvider.Context ctx) {
    }

    @Override
    public void render(BarStackBE be, float partialTick, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        SlotAccess handler = be.getItems();

        if (be.getLevel() == null) return;

        VertexConsumer vc = buffers.getBuffer(RenderType.solid());

        for (int idx = 0; idx < BarStackBE.SLOTS; idx++) {
            ItemStack stack = handler.getStackInSlot(idx);
            if (stack.isEmpty()) continue;

            BarTextureStore.BarTextureData textureData = BarTextureStore.getTexture(stack);
            TextureAtlasSprite sprite = SPRITES.computeIfAbsent(textureData.texture(), texture ->
                    Minecraft.getInstance().getTextureAtlas(InventoryMenu.BLOCK_ATLAS).apply(texture));
            AABB box = BarCubeIdx.localBox(idx);

            pose.pushPose();
            pose.translate(box.minX, box.minY, box.minZ);
            pose.scale((float) (box.maxX - box.minX), (float) (box.maxY - box.minY),
                    (float) (box.maxZ - box.minZ));

            emitBar(pose, vc, sprite, light, idx / BarCubeIdx.LAYER_SIZE, textureData);

            pose.popPose();
        }
    }

    private static void emitBar(PoseStack pose, VertexConsumer vc, TextureAtlasSprite sp, int light, int layer,
                                BarTextureStore.BarTextureData textureData) {
        int overlay = OverlayTexture.NO_OVERLAY;
        UvRegion[] regions = SPRITE_REGIONS.computeIfAbsent(sp, sprite -> {
            float u0 = sprite.getU0(), v0 = sprite.getV0();
            float uRange = sprite.getU1() - u0, vRange = sprite.getV1() - v0;
            // Three face regions on the texture's 32-unit grid.
            return new UvRegion[]{
                    region(u0, v0, uRange, vRange, 0f, 0f, 24f, 12f),
                    region(u0, v0, uRange, vRange, 0f, 12f, 24f, 20f),
                    region(u0, v0, uRange, vRange, 0f, 20f, 12f, 28f)};
        });
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
        var m = pose.last().pose();
        var n = pose.last().normal();
        float normalX = n.m00() * nx + n.m10() * ny + n.m20() * nz;
        float normalY = n.m01() * nx + n.m11() * ny + n.m21() * nz;
        float normalZ = n.m02() * nx + n.m12() * ny + n.m22() * nz;
        vertex(vc, m, x1,y1,z1, u0,v0, light,overlay, normalX,normalY,normalZ, r,g,b,a);
        vertex(vc, m, x2,y2,z2, u1,v0, light,overlay, normalX,normalY,normalZ, r,g,b,a);
        vertex(vc, m, x3,y3,z3, u1,v1, light,overlay, normalX,normalY,normalZ, r,g,b,a);
        vertex(vc, m, x4,y4,z4, u0,v1, light,overlay, normalX,normalY,normalZ, r,g,b,a);
    }

    /** Transforms scalar coordinates without allocating temporary position or normal vectors. */
    private static void vertex(VertexConsumer vc, Matrix4f m, float x, float y, float z,
                               float u, float v, int light, int overlay,
                               float nx, float ny, float nz, int r, int g, int b, int a) {
        vc.vertex(m.m00() * x + m.m10() * y + m.m20() * z + m.m30(),
                        m.m01() * x + m.m11() * y + m.m21() * z + m.m31(),
                        m.m02() * x + m.m12() * y + m.m22() * z + m.m32())
                .color(r, g, b, a).uv(u, v).overlayCoords(overlay).uv2(light)
                .normal(nx, ny, nz).endVertex();
    }
}
