package com.github.crittscott.somestacks.client;

import com.github.crittscott.somestacks.block.BarStackBE;
import com.github.crittscott.somestacks.util.BarCubeIdx;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.BlockPos;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.items.IItemHandler;

public class BarStackBER implements BlockEntityRenderer<BarStackBE> {

    public BarStackBER(BlockEntityRendererProvider.Context ctx) {
    }

    @Override
    public void render(BarStackBE be, float partialTick, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        IItemHandler handler = be.getItems();

        if (be.getLevel() == null) return;

        BlockPos bePos = be.getBlockPos();
        int barLight = LevelRenderer.getLightColor(be.getLevel(), bePos);

        VertexConsumer vc = buffers.getBuffer(RenderType.solid());

        for (int idx = 0; idx < BarStackBE.SLOTS; idx++) {
            ItemStack stack = handler.getStackInSlot(idx);
            if (stack.isEmpty()) continue;

            BarTextureStore.BarTextureData textureData = BarTextureStore.getTexture(stack);
            TextureAtlasSprite sprite = Minecraft.getInstance()
                    .getTextureAtlas(InventoryMenu.BLOCK_ATLAS)
                    .apply(textureData.texture());

            int[] xyz = BarCubeIdx.xyzFromIndex(idx);

            float sx = (float) (BarCubeIdx.startPixelX(xyz[0], xyz[1]) / 16.0);
            float sy = (float) (BarCubeIdx.startPixelY(xyz[1]) / 16.0);
            float sz = (float) (BarCubeIdx.startPixelZ(xyz[2], xyz[1]) / 16.0);
            float width = (float) (BarCubeIdx.barWidth(xyz[1]) / 16.0);
            float height = (float) (BarCubeIdx.barHeight(xyz[1]) / 16.0);
            float depth = (float) (BarCubeIdx.barDepth(xyz[1]) / 16.0);

            pose.pushPose();
            pose.translate(sx, sy, sz);
            pose.scale(width, height, depth);

            emitBar(pose, vc, sprite, barLight, xyz[1], textureData);

            pose.popPose();
        }
    }

    private static void emitBar(PoseStack pose, VertexConsumer vc, TextureAtlasSprite sp, int light, int layer,
                                BarTextureStore.BarTextureData textureData) {
        int overlay = OverlayTexture.NO_OVERLAY;
        float u0 = sp.getU0(), v0 = sp.getV0(), u1 = sp.getU1(), v1 = sp.getV1();

        float uRange = u1 - u0;
        float vRange = v1 - v0;

        // The sprite stacks three regions down a 32-unit grid: the 24x12 top/bottom face
        // at the origin, the 24x8 long side below it, and the 12x8 end cap below that.
        UvRegion top = region(u0, v0, uRange, vRange, 0f, 0f, 24f, 12f);
        UvRegion longSide = region(u0, v0, uRange, vRange, 0f, 12f, 24f, 20f);
        UvRegion endCap = region(u0, v0, uRange, vRange, 0f, 20f, 12f, 28f);

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
        vc.vertex(m, x1,y1,z1).color(r,g,b,a).uv(u0,v0).overlayCoords(overlay).uv2(light).normal(n, nx,ny,nz).endVertex();
        vc.vertex(m, x2,y2,z2).color(r,g,b,a).uv(u1,v0).overlayCoords(overlay).uv2(light).normal(n, nx,ny,nz).endVertex();
        vc.vertex(m, x3,y3,z3).color(r,g,b,a).uv(u1,v1).overlayCoords(overlay).uv2(light).normal(n, nx,ny,nz).endVertex();
        vc.vertex(m, x4,y4,z4).color(r,g,b,a).uv(u0,v1).overlayCoords(overlay).uv2(light).normal(n, nx,ny,nz).endVertex();
    }

    @Override
    public boolean shouldRenderOffScreen(BarStackBE be) {
        return false;
    }
}
