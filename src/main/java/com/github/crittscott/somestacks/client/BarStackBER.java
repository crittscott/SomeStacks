package com.github.crittscott.somestacks.client;

import com.github.crittscott.somestacks.block.BarStackBE;
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
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.items.IItemHandler;

import com.github.crittscott.somestacks.util.BarCubeIdx;

public class BarStackBER implements BlockEntityRenderer<BarStackBE> {

    public BarStackBER(BlockEntityRendererProvider.Context ctx) {
    }

    @Override
    public void render(BarStackBE be, float partialTick, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        IItemHandler handler = be.getCapability(ForgeCapabilities.ITEM_HANDLER).orElse(null);
        if (handler == null) return;

        if (be.getLevel() == null) return;

        BlockPos bePos = be.getBlockPos();
        int barLight = LevelRenderer.getLightColor(be.getLevel(), bePos);

        VertexConsumer vc = buffers.getBuffer(RenderType.solid());

        for (int idx = 0; idx < 64; idx++) {
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

        boolean rotated = (layer % 2) == 1;

        float topU0, topU1, topV0, topV1;
        float longU0, longU1, longV0, longV1;
        float endU0, endU1, endV0, endV1;

        if (rotated) {
            // Odd layers: bars rotated 90°, dimensions are 3×2×6 (X×Y×Z)
            // Top/Bottom faces: Use the 6×3 region, rotation happens in the quads
            topU0 = u0 + uRange * (0f / 32f);
            topU1 = u0 + uRange * (24f / 32f);
            topV0 = v0 + vRange * (0f / 32f);
            topV1 = v0 + vRange * (12f / 32f);

            // Z faces are now ends: 3×2 pixels from (0,5) to (3,7)
            endU0 = u0 + uRange * (0f / 32f);
            endU1 = u0 + uRange * (12f / 32f);
            endV0 = v0 + vRange * (20f / 32f);
            endV1 = v0 + vRange * (28f / 32f);

            // X faces are now long sides: 6×2 pixels from (0,3) to (6,5)
            longU0 = u0 + uRange * (0f / 32f);
            longU1 = u0 + uRange * (24f / 32f);
            longV0 = v0 + vRange * (12f / 32f);
            longV1 = v0 + vRange * (20f / 32f);
        } else {
            // Even layers: bars normal orientation, dimensions are 6×2×3 (X×Y×Z)
            // Top/Bottom faces: 6×3 pixels from (0,0) to (6,3)
            topU0 = u0 + uRange * (0f / 32f);
            topU1 = u0 + uRange * (24f / 32f);
            topV0 = v0 + vRange * (0f / 32f);
            topV1 = v0 + vRange * (12f / 32f);

            // Z faces (long sides): 6×2 pixels from (0,3) to (6,5)
            longU0 = u0 + uRange * (0f / 32f);
            longU1 = u0 + uRange * (24f / 32f);
            longV0 = v0 + vRange * (12f / 32f);
            longV1 = v0 + vRange * (20f / 32f);

            // X faces (ends): 3×2 pixels from (0,5) to (3,7)
            endU0 = u0 + uRange * (0f / 32f);
            endU1 = u0 + uRange * (12f / 32f);
            endV0 = v0 + vRange * (20f / 32f);
            endV1 = v0 + vRange * (28f / 32f);
        }

        int r = textureData.red();
        int g = textureData.green();
        int b = textureData.blue();
        int a = textureData.alpha();

        // +Z face
        quad(pose, vc, light, overlay, 0,0,1, 1,0,1, 1,1,1, 0,1,1, longU0,longV1,longU1,longV0, 0,0,1, r,g,b,a);
        // -Z face
        quad(pose, vc, light, overlay, 1,0,0, 0,0,0, 0,1,0, 1,1,0, longU0,longV1,longU1,longV0, 0,0,-1, r,g,b,a);
        // +X face
        quad(pose, vc, light, overlay, 1,0,1, 1,0,0, 1,1,0, 1,1,1, endU0,endV1,endU1,endV0, 1,0,0, r,g,b,a);
        // -X face
        quad(pose, vc, light, overlay, 0,0,0, 0,0,1, 0,1,1, 0,1,0, endU0,endV1,endU1,endV0, -1,0,0, r,g,b,a);

        if (rotated) {
            // +Y face (top) - reordered vertices to rotate texture 90°
            quad(pose, vc, light, overlay, 0,1,0, 0,1,1, 1,1,1, 1,1,0, topU0,topV0,topU1,topV1, 0,1,0, r,g,b,a);
            // -Y face (bottom) - reordered vertices to rotate texture 90°
            quad(pose, vc, light, overlay, 0,0,0, 0,0,1, 1,0,1, 1,0,0, topU0,topV0,topU1,topV1, 0,-1,0, r,g,b,a);
        } else {
            // +Y face (top)
            quad(pose, vc, light, overlay, 0,1,1, 1,1,1, 1,1,0, 0,1,0, topU0,topV1,topU1,topV0, 0,1,0, r,g,b,a);
            // -Y face (bottom)
            quad(pose, vc, light, overlay, 0,0,0, 1,0,0, 1,0,1, 0,0,1, topU0,topV1,topU1,topV0, 0,-1,0, r,g,b,a);
        }
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
