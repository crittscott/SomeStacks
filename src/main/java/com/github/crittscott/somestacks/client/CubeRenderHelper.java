package com.github.crittscott.somestacks.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.client.extensions.common.IClientItemExtensions;

import java.util.List;

public final class CubeRenderHelper {
    private CubeRenderHelper() {}

    public static final ResourceLocation STACK_CUBE_TEXTURE = new ResourceLocation("somestacks", "block/stack_cube");

    public static void renderItemInCube(ItemStack stack, PoseStack pose, MultiBufferSource buffers, int light,
                                        BlockRenderDispatcher blockRenderer, Level level) {
        RenderMode mode = getRenderMode(stack, level);

        switch (mode) {
            case TWO_D -> render2DItem(stack, pose, buffers, light, level);
            case THREE_D -> render3DItem(stack, pose, buffers, light, level);
            case BLOCK -> renderBlockItem(stack, pose, buffers, light, blockRenderer, level);
            case GUI -> renderGuiItem(stack, pose, buffers, light, level);
        }
    }

    private static RenderMode getRenderMode(ItemStack stack, Level level) {
        // Check for manual override first
        RenderMode override = ItemRenderOverrides.getMode(stack);
        if (override != null) {
            return override;
        }

        // Auto-detect: never auto-choose BLOCK, only TWO_D or THREE_D
        BakedModel model = Minecraft.getInstance()
                .getItemRenderer()
                .getModel(stack, level, null, 0);
        return model.isGui3d() ? RenderMode.THREE_D : RenderMode.TWO_D;
    }

    private static boolean hasBEWLR(ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        }

        try {
            // Get the client extensions for this item
            var extensions = IClientItemExtensions.of(stack);

            // Get the custom renderer (BEWLR)
            var customRenderer = extensions.getCustomRenderer();

            // Check if it's using a custom BEWLR (not the default one)
            // The default is BlockEntityWithoutLevelRenderer itself
            // Custom implementations will be subclasses
            return customRenderer.getClass() != BlockEntityWithoutLevelRenderer.class;
        } catch (Exception e) {
            // If anything goes wrong, assume no custom BEWLR
            return false;
        }
    }

    private static float getBEWLRScaleCorrection(ItemStack stack) {
        return hasBEWLR(stack) ? 0.5f : 1.0f;
    }

    private static void render3DItem(ItemStack stack, PoseStack pose, MultiBufferSource buffers, int light, Level level) {
        float customScale = ItemRenderOverrides.getScale(stack);
        float bewlrCorrection = getBEWLRScaleCorrection(stack);
        float finalScale = customScale * bewlrCorrection;
        float[] offset = ItemRenderOverrides.getOffset(stack);

        pose.pushPose();
        pose.scale(finalScale, finalScale, finalScale);
        pose.translate(0.25 / finalScale, 0.25 / finalScale, 0.25 / finalScale);
        pose.translate(offset[0] / finalScale, offset[1] / finalScale, offset[2] / finalScale);

        Minecraft.getInstance().getItemRenderer().renderStatic(
                stack,
                ItemDisplayContext.FIXED,
                light,
                OverlayTexture.NO_OVERLAY,
                pose,
                buffers,
                level,
                0
        );

        pose.popPose();
    }

    private static void renderGuiItem(ItemStack stack, PoseStack pose, MultiBufferSource buffers, int light, Level level) {
        float customScale = ItemRenderOverrides.getScale(stack);
        float bewlrCorrection = getBEWLRScaleCorrection(stack);
        float finalScale = customScale * bewlrCorrection;
        float[] offset = ItemRenderOverrides.getOffset(stack);

        pose.pushPose();
        pose.scale(finalScale, finalScale, finalScale);
        pose.translate(0.25 / finalScale, 0.25 / finalScale, 0.25 / finalScale);
        pose.translate(offset[0] / finalScale, offset[1] / finalScale, offset[2] / finalScale);

        // Counter-rotate the GUI display context transforms
        pose.mulPose(Axis.YP.rotationDegrees(-45.0f));
        pose.mulPose(Axis.XP.rotationDegrees(-30.0f));

        Minecraft.getInstance().getItemRenderer().renderStatic(
                stack,
                ItemDisplayContext.GUI,
                light,
                OverlayTexture.NO_OVERLAY,
                pose,
                buffers,
                level,
                0
        );

        pose.popPose();
    }

    private static void render2DItem(ItemStack stack, PoseStack pose, MultiBufferSource buffers, int light, Level level) {
        pose.pushPose();
        pose.scale(0.5f, 0.5f, 0.5f);
        BakedModel model = Minecraft.getInstance().getItemRenderer().getModel(stack, level, null, 0);
        render2DItemCube(pose, buffers, stack, model, light);
        pose.popPose();
    }

    private static void renderBlockItem(ItemStack stack, PoseStack pose, MultiBufferSource buffers, int light,
                                        BlockRenderDispatcher blockRenderer, Level level) {
        if (!(stack.getItem() instanceof BlockItem blockItem)) {
            // Fallback if someone misconfigured a non-BlockItem as "block" mode
            return;
        }

        BlockState blockState = blockItem.getBlock().defaultBlockState();
        float customScale = ItemRenderOverrides.getScale(stack);
        float bewlrCorrection = getBEWLRScaleCorrection(stack);
        float finalScale = customScale * bewlrCorrection;
        float[] offset = ItemRenderOverrides.getOffset(stack);

        pose.pushPose();
        pose.scale(finalScale, finalScale, finalScale);
        pose.translate(0.25 / finalScale, 0.25 / finalScale, 0.25 / finalScale);
        pose.translate(offset[0] / finalScale, offset[1] / finalScale, offset[2] / finalScale);
        pose.mulPose(Axis.YP.rotationDegrees(180));
        pose.translate(-0.5, -0.5, -0.5);

        try {
            blockRenderer.renderSingleBlock(blockState, pose, buffers, light, OverlayTexture.NO_OVERLAY);
        } catch (Exception e) {
            // Some blocks (like IE multiblocks) have complex models that require level context
            // and will crash when rendered without it. Fall back to item rendering.
            pose.popPose();
            render3DItem(stack, pose, buffers, light, level);
            return;
        }

        pose.popPose();
    }

    public static void render2DItemCube(PoseStack pose, MultiBufferSource buffers, ItemStack stack, BakedModel model, int light) {
        TextureAtlasSprite backgroundSprite = Minecraft.getInstance()
                .getTextureAtlas(InventoryMenu.BLOCK_ATLAS)
                .apply(STACK_CUBE_TEXTURE);
        VertexConsumer solidVc = buffers.getBuffer(RenderType.solid());
        emitCube(pose, solidVc, backgroundSprite, light);

        List<BakedQuad> quads = model.getQuads(null, null, RandomSource.create(0));
        VertexConsumer vc = buffers.getBuffer(RenderType.cutout());

        for (BakedQuad quad : quads) {
            int tintIndex = quad.getTintIndex();
            int color = tintIndex >= 0
                    ? Minecraft.getInstance().getItemColors().getColor(stack, tintIndex)
                    : 0xFFFFFFFF;

            int r = (color >> 16) & 0xFF;
            int g = (color >> 8) & 0xFF;
            int b = color & 0xFF;

            QuadVertex[] vertices = extractQuadVertices(quad);

            float customScale = ItemRenderOverrides.getScale(stack);
            float[] offset = ItemRenderOverrides.getOffset(stack);

            // Shrink 2D texture from 16x16 to 15x15 to leave 1px border, then apply custom scale
            // Scale around center point: new_coord = (old_coord - 0.5) * 0.875 * customScale + 0.5
            for (int i = 0; i < vertices.length; i++) {
                float x = vertices[i].x;
                float y = vertices[i].y;
                float z = vertices[i].z;

                x = (x - 0.5f) * 0.875f * customScale + 0.5f;
                y = (y - 0.5f) * 0.875f * customScale + 0.5f;

                // Apply offset (x, y only for 2D items)
                x += offset[0];
                y += offset[1];

                vertices[i] = new QuadVertex(x, y, z, vertices[i].u, vertices[i].v);
            }

            for (Direction face : Direction.values()) {
                renderQuadOnFace(pose, vc, vertices, r, g, b, 255, light, face);
            }
        }
    }

    public static QuadVertex[] extractQuadVertices(BakedQuad quad) {
        int[] vertexData = quad.getVertices();
        QuadVertex[] vertices = new QuadVertex[4];

        for (int i = 0; i < 4; i++) {
            int offset = i * 8;
            float x = Float.intBitsToFloat(vertexData[offset]);
            float y = Float.intBitsToFloat(vertexData[offset + 1]);
            float z = Float.intBitsToFloat(vertexData[offset + 2]);
            float u = Float.intBitsToFloat(vertexData[offset + 4]);
            float v = Float.intBitsToFloat(vertexData[offset + 5]);
            vertices[i] = new QuadVertex(x, y, z, u, v);
        }

        return vertices;
    }

    public static void renderQuadOnFace(PoseStack pose, VertexConsumer vc, QuadVertex[] vertices,
                                        int r, int g, int b, int a, int light, Direction face) {
        var m = pose.last().pose();
        var n = pose.last().normal();

        for (QuadVertex v : vertices) {
            float[] pos = transformToFace(v.x, v.y, v.z, face);
            float nx = 0, ny = 0, nz = 0;

            switch (face) {
                case NORTH: nz = -1; break;
                case SOUTH: nz = 1; break;
                case WEST: nx = -1; break;
                case EAST: nx = 1; break;
                case DOWN: ny = -1; break;
                case UP: ny = 1; break;
            }

            vc.vertex(m, pos[0], pos[1], pos[2])
                    .color(r, g, b, a)
                    .uv(v.u, v.v)
                    .overlayCoords(OverlayTexture.NO_OVERLAY)
                    .uv2(light)
                    .normal(n, nx, ny, nz)
                    .endVertex();
        }
    }

    public static float[] transformToFace(float itemX, float itemY, float itemZ, Direction face) {
        float offset = 0.001f;
        float depthScale = 0.01f;
        return switch (face) {
            case SOUTH -> new float[]{itemX, itemY, 1.0f + offset + (itemZ * depthScale)};
            case NORTH -> new float[]{1.0f - itemX, itemY, 0.0f - offset - (itemZ * depthScale)};
            case EAST -> new float[]{1.0f + offset + (itemZ * depthScale), itemY, itemX};
            case WEST -> new float[]{0.0f - offset - (itemZ * depthScale), itemY, 1.0f - itemX};
            case UP -> new float[]{itemX, 1.0f + offset + (itemZ * depthScale), itemY};
            case DOWN -> new float[]{itemX, 0.0f - offset - (itemZ * depthScale), 1.0f - itemY};
            default -> new float[]{itemX, itemY, 0.0f};
        };
    }

    public static void emitCube(PoseStack pose, VertexConsumer vc, TextureAtlasSprite sp, int light) {
        emitCube(pose, vc, sp, light, 255, 255, 255, 255);
    }

    public static void emitCube(PoseStack pose, VertexConsumer vc, TextureAtlasSprite sp, int light, int r, int g, int b, int a) {
        int overlay = OverlayTexture.NO_OVERLAY;
        float u0 = sp.getU0(), v0 = sp.getV0(), u1 = sp.getU1(), v1 = sp.getV1();
        // +Z
        quad(pose, vc, light, overlay, 0,0,1, 1,0,1, 1,1,1, 0,1,1, u0,v1,u1,v0, 0,0,1, r,g,b,a);
        // -Z
        quad(pose, vc, light, overlay, 1,0,0, 0,0,0, 0,1,0, 1,1,0, u0,v1,u1,v0, 0,0,-1, r,g,b,a);
        // +X
        quad(pose, vc, light, overlay, 1,0,1, 1,0,0, 1,1,0, 1,1,1, u0,v1,u1,v0, 1,0,0, r,g,b,a);
        // -X
        quad(pose, vc, light, overlay, 0,0,0, 0,0,1, 0,1,1, 0,1,0, u0,v1,u1,v0, -1,0,0, r,g,b,a);
        // +Y
        quad(pose, vc, light, overlay, 0,1,1, 1,1,1, 1,1,0, 0,1,0, u0,v1,u1,v0, 0,1,0, r,g,b,a);
        // -Y
        quad(pose, vc, light, overlay, 0,0,0, 1,0,0, 1,0,1, 0,0,1, u0,v1,u1,v0, 0,-1,0, r,g,b,a);
    }

    public static void quad(PoseStack pose, VertexConsumer vc, int light, int overlay,
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

    public record QuadVertex(float x, float y, float z, float u, float v) {
    }
}
