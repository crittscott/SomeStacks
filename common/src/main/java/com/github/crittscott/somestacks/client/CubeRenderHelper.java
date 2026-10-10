package com.github.crittscott.somestacks.client;

import com.github.crittscott.somestacks.SomeStacksCommon;
import com.github.crittscott.somestacks.util.CubeGrid;
import com.github.crittscott.somestacks.util.QuarterTurns;
import com.github.crittscott.somestacks.util.SlotAccess;
import com.github.crittscott.somestacks.renderconfig.RenderOffset;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.Direction;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.ARGB;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import net.minecraft.core.component.DataComponentPatch;
import java.util.Set;
import java.util.function.IntUnaryOperator;

/**
 * Draws one stored item inside one cell according to its render profile. The Storage
 * and Singles renderers position a cell and delegate here; everything about how the item itself is
 * presented lives in this class.
 *
 * <p>The {@code 3d}, {@code gui}, and {@code block} modes hand off to Minecraft's own renderers.
 * The {@code 2d} mode is the one implemented here: flat item art projected onto the visible faces
 * of a small background cube, which is what makes a wall of stored items readable at a distance.
 */
public final class CubeRenderHelper {
    private CubeRenderHelper() {}

    public static final ResourceLocation STACK_CUBE_TEXTURE = ResourceLocation.fromNamespaceAndPath(SomeStacksCommon.MODID, "block/stack_cube");
    /** The {@code 2d} background drawn without art for an item whose model threw when captured. */
    public static final ResourceLocation STACK_CUBE_ERROR_TEXTURE =
            ResourceLocation.fromNamespaceAndPath(SomeStacksCommon.MODID, "block/stack_cube_error");
    public static final ResourceLocation RENDER_CACHE_RELOAD_LISTENER_ID =
            ResourceLocation.fromNamespaceAndPath(SomeStacksCommon.MODID, "render_caches");

    /** {@link Direction#values()} clones its array on every call, and this is a per-item loop. */
    private static final Direction[] DIRECTIONS = Direction.values();

    /** How far outside the cell face the projected art sits, clear of the background cube. */
    private static final float FACE_OFFSET = 0.001f;
    /** How much of a quad's own depth survives the projection, keeping a multi-pass item's layers apart. */
    private static final float FACE_DEPTH = 0.01f;
    /** Fraction of the face the art spans, leaving a one-pixel border. */
    private static final float ART_INSET = 0.875f;

    /** Fraction of the cell the 2d background cube spans, leaving it visibly separated from its neighbors. */
    private static final float CUBE_INSET = 0.9f;

    /**
     * Scale a stack block's renderer applies before handing a cell's contents here: an item's unit
     * cube becomes eight pixels.
     */
    public static final float CELL_RENDER_SCALE = 8.0f / 16.0f;

    /**
     * Storage's own cell-render scale, grown so a cell's rendered content fills more of its
     * 4.5-pixel cell, halving the gap between Storage's cells. Storage-only: Singles' cells already
     * tile edge to edge with no gap to absorb growth into.
     */
    public static final float STORAGE_CELL_RENDER_SCALE = CELL_RENDER_SCALE * 4.5f / 4.0f;

    /** One four-pixel cell in those local units, where 1.0 is the eight pixels above. */
    public static final float CELL_LOCAL_SIZE = 4.0f / 8.0f;

    /** The cell's center in the same units, which a cell's contents are drawn about. */
    public static final float CELL_LOCAL_CENTRE = CELL_LOCAL_SIZE / 2.0f;

    /**
     * Rotations mapping the baked model's +Z art plane onto each cube face. All are proper rotations,
     * preserving the art's orientation. SOUTH already matches the source plane and needs none.
     */
    private static final Quaternionf[] FACE_ROTATIONS = new Quaternionf[DIRECTIONS.length];

    /** Corners of each cube face in cell space, wound to face outward. */
    private static final float[][] FACE_CORNERS = new float[DIRECTIONS.length][];

    static {
        FACE_ROTATIONS[Direction.NORTH.ordinal()] = Axis.YP.rotationDegrees(180.0f);
        FACE_ROTATIONS[Direction.EAST.ordinal()] = Axis.YP.rotationDegrees(90.0f);
        FACE_ROTATIONS[Direction.WEST.ordinal()] = Axis.YP.rotationDegrees(-90.0f);
        FACE_ROTATIONS[Direction.UP.ordinal()] = Axis.XP.rotationDegrees(-90.0f);
        FACE_ROTATIONS[Direction.DOWN.ordinal()] = Axis.XP.rotationDegrees(90.0f);

        FACE_CORNERS[Direction.SOUTH.ordinal()] = new float[]{0,0,1, 1,0,1, 1,1,1, 0,1,1};
        FACE_CORNERS[Direction.NORTH.ordinal()] = new float[]{1,0,0, 0,0,0, 0,1,0, 1,1,0};
        FACE_CORNERS[Direction.EAST.ordinal()] = new float[]{1,0,1, 1,0,0, 1,1,0, 1,1,1};
        FACE_CORNERS[Direction.WEST.ordinal()] = new float[]{0,0,0, 0,0,1, 0,1,1, 0,1,0};
        FACE_CORNERS[Direction.UP.ordinal()] = new float[]{0,1,1, 1,1,1, 1,1,0, 0,1,0};
        FACE_CORNERS[Direction.DOWN.ordinal()] = new float[]{0,0,0, 1,0,0, 1,0,1, 0,0,1};
    }

    /** Draws every occupied position in a regular Storage or Singles cube grid. */
    public static void renderGridItems(
            SlotAccess items, int slots, CubeGrid grid, int blockRotation,
            float renderScale, IntUnaryOperator itemRotation,
            Level level, BlockPos blockPos, PoseStack pose, MultiBufferSource buffers,
            BlockRenderDispatcher blockRenderer) {
        int cubeLight = LevelRenderer.getLightColor(level, blockPos);
        Map<CaptureKey, List<ItemCapture.TintedQuad>> captures = new HashMap<>();
        for (int index = 0; index < slots; index++) {
            ItemStack stack = items.getStackInSlot(index);
            if (stack.isEmpty()) {
                continue;
            }

            AABB bounds = grid.localBox(index, blockRotation);

            pose.pushPose();
            pose.translate(bounds.minX, bounds.minY, bounds.minZ);
            pose.scale(renderScale, renderScale, renderScale);

            int quarterTurns = itemRotation.applyAsInt(index);
            if (quarterTurns != 0) {
                pose.translate(CELL_LOCAL_CENTRE, CELL_LOCAL_CENTRE, CELL_LOCAL_CENTRE);
                pose.mulPose(Axis.YP.rotationDegrees(QuarterTurns.degrees(quarterTurns)));
                pose.translate(-CELL_LOCAL_CENTRE, -CELL_LOCAL_CENTRE, -CELL_LOCAL_CENTRE);
            }

            renderItemInCube(stack, pose, buffers, cubeLight, blockRenderer, level, captures);
            pose.popPose();
        }
    }

    /**
     * Items whose block form threw when drawn. Cached so the attempt is made once per bake
     * rather than once per frame for as long as the item is on screen: a renderer that throws
     * partway through a quad leaves the shared buffer mid-quad, and repeating that every frame
     * compounds it.
     */
    private static final Set<Item> BLOCK_RENDER_FAILURES = Collections.newSetFromMap(new IdentityHashMap<>());

    /**
     * Items whose model threw while its {@code 2d} art was captured. Cached so the model is asked
     * once per bake; these draw the error cube until the next resource reload.
     */
    private static final Set<Item> FLAT_CAPTURE_FAILURES = Collections.newSetFromMap(new IdentityHashMap<>());

    /** The pose {@code 2d} captures run under; render-thread only, and never pushed. */
    private static final PoseStack SCRATCH_POSE = new PoseStack();

    private record CaptureKey(Item item, DataComponentPatch components, int count) {
        static CaptureKey of(ItemStack stack) {
            return new CaptureKey(stack.getItem(), stack.getComponentsPatch(), stack.getCount());
        }
    }

    private static void renderItemInCube(ItemStack stack, PoseStack pose, MultiBufferSource buffers,
            int light, BlockRenderDispatcher blockRenderer, Level level,
            Map<CaptureKey, List<ItemCapture.TintedQuad>> captures) {
        RenderProfile profile = ItemRenderOverrides.resolve(stack);
        if (profile == null) {
            return;
        }
        float scale = profile.scale();
        RenderOffset offset = profile.offset();

        switch (profile.mode()) {
            case TWO_D -> render2DItem(stack, pose, buffers, light, level, scale, offset, captures);
            case THREE_D -> render3DItem(stack, pose, buffers, light, level, scale, offset);
            case BLOCK -> renderBlockItem(stack, pose, buffers, light, blockRenderer, level, scale, offset);
            case GUI -> renderGuiItem(stack, pose, buffers, light, level, scale, offset);
        }
    }

    private static void render3DItem(ItemStack stack, PoseStack pose, MultiBufferSource buffers, int light, Level level,
                                     float finalScale, RenderOffset offset) {
        pose.pushPose();
        pose.scale(finalScale, finalScale, finalScale);
        pose.translate(CELL_LOCAL_CENTRE / finalScale, CELL_LOCAL_CENTRE / finalScale,
                CELL_LOCAL_CENTRE / finalScale);
        pose.translate(offset.x() / finalScale, offset.y() / finalScale, offset.z() / finalScale);

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

    private static void renderGuiItem(ItemStack stack, PoseStack pose, MultiBufferSource buffers, int light, Level level,
                                      float finalScale, RenderOffset offset) {
        pose.pushPose();
        pose.scale(finalScale, finalScale, finalScale);
        pose.translate(CELL_LOCAL_CENTRE / finalScale, CELL_LOCAL_CENTRE / finalScale,
                CELL_LOCAL_CENTRE / finalScale);
        pose.translate(offset.x() / finalScale, offset.y() / finalScale, offset.z() / finalScale);

        applyGuiCounterRotation(pose);

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

    /** Applies the counter-rotation shared by GUI rendering and automatic GUI measurement. */
    public static void applyGuiCounterRotation(PoseStack pose) {
        pose.mulPose(Axis.YP.rotationDegrees(-45.0f));
        pose.mulPose(Axis.XP.rotationDegrees(-30.0f));
    }

    private static void render2DItem(ItemStack stack, PoseStack pose, MultiBufferSource buffers, int light, Level level,
                                     float scale, RenderOffset offset,
                                     Map<CaptureKey, List<ItemCapture.TintedQuad>> captures) {
        pose.pushPose();
        pose.scale(CELL_LOCAL_SIZE, CELL_LOCAL_SIZE, CELL_LOCAL_SIZE);
        pose.translate(0.5f, 0.5f, 0.5f);
        pose.scale(CUBE_INSET, CUBE_INSET, CUBE_INSET);
        pose.translate(-0.5f, -0.5f, -0.5f);
        List<ItemCapture.TintedQuad> quads = captures.computeIfAbsent(CaptureKey.of(stack),
                ignored -> captureFlatArt(stack, level));
        if (quads == null) {
            render2DItemCube(pose, buffers, List.of(), STACK_CUBE_ERROR_TEXTURE, light, scale, offset);
        } else {
            render2DItemCube(pose, buffers, quads, STACK_CUBE_TEXTURE, light, scale, offset);
        }
        pose.popPose();
    }

    /**
     * The baked quads {@code 2d} lays onto the cube, or null when the item's model has thrown. The
     * capture records without drawing, so a model that throws partway leaves no buffer mid-quad.
     */
    @Nullable
    private static List<ItemCapture.TintedQuad> captureFlatArt(ItemStack stack, Level level) {
        Item item = stack.getItem();
        if (FLAT_CAPTURE_FAILURES.contains(item)) {
            return null;
        }
        try {
            return List.copyOf(ItemCapture.captureQuads(
                    stack, ItemDisplayContext.FIXED, level, SCRATCH_POSE).quads());
        } catch (RuntimeException e) {
            FLAT_CAPTURE_FAILURES.add(item);
            SomeStacksCommon.LOGGER.warn("Item model threw while capturing 2d art for {}; drawing the error cube instead",
                    BuiltInRegistries.ITEM.getKey(item), e);
            return null;
        }
    }

    private static void renderBlockItem(ItemStack stack, PoseStack pose, MultiBufferSource buffers, int light,
                                        BlockRenderDispatcher blockRenderer, Level level,
                                        float finalScale, RenderOffset offset) {
        if (!(stack.getItem() instanceof BlockItem blockItem)) {
            // Block mode has no meaningful rendering path for a non-BlockItem.
            return;
        }

        BlockState blockState = blockItem.getBlock().defaultBlockState();

        // Only a MODEL block has a block form to draw here. Any other shape is drawn by a block
        // entity renderer, and the single-block path would hand our pose stack and a throwaway
        // ItemStack to that renderer with the NONE display context; 3d reaches the same renderer
        // with the real stack under FIXED, and cannot leave the pose stack unbalanced.
        if (blockState.getRenderShape() != RenderShape.MODEL
                || BLOCK_RENDER_FAILURES.contains(stack.getItem())) {
            render3DItem(stack, pose, buffers, light, level, finalScale, offset);
            return;
        }

        pose.pushPose();
        pose.scale(finalScale, finalScale, finalScale);
        pose.translate(CELL_LOCAL_CENTRE / finalScale, CELL_LOCAL_CENTRE / finalScale,
                CELL_LOCAL_CENTRE / finalScale);
        pose.translate(offset.x() / finalScale, offset.y() / finalScale, offset.z() / finalScale);
        pose.mulPose(Axis.YP.rotationDegrees(180));
        pose.translate(-0.5, -0.5, -0.5);

        try {
            blockRenderer.renderSingleBlock(blockState, pose, buffers, light, OverlayTexture.NO_OVERLAY);
        } catch (Exception e) {
            // A third-party block model that cannot be baked or drawn on its own. The model path
            // works from the pose it is handed and never pushes, so the stack is where we left it.
            pose.popPose();
            if (BLOCK_RENDER_FAILURES.add(stack.getItem())) {
                SomeStacksCommon.LOGGER.warn("Block rendering threw for {}; drawing it as 3d instead",
                        BuiltInRegistries.ITEM.getKey(stack.getItem()), e);
            }
            render3DItem(stack, pose, buffers, light, level, finalScale, offset);
            return;
        }

        pose.popPose();
    }

    private static void render2DItemCube(PoseStack pose, MultiBufferSource buffers, List<ItemCapture.TintedQuad> quads,
                                         ResourceLocation background, int light, float scale, RenderOffset offset) {
        List<FaceTarget> faces = visibleFaces(pose, scale, offset);
        if (faces.isEmpty()) {
            return;
        }

        TextureAtlasSprite backgroundSprite = Minecraft.getInstance()
                .getTextureAtlas(TextureAtlas.LOCATION_BLOCKS)
                .apply(background);
        VertexConsumer solidVc = buffers.getBuffer(RenderType.solid());
        for (FaceTarget target : faces) {
            emitBackgroundFace(pose.last().pose(), solidVc, backgroundSprite, light, target);
        }

        if (quads.isEmpty()) {
            return;
        }
        VertexConsumer vc = buffers.getBuffer(RenderType.cutout());
        emitPlates(vc, quads, light, faces);
    }

    /** Clears failure state tied to models being replaced by a resource reload. */
    public static void onResourceReload() {
        BLOCK_RENDER_FAILURES.clear();
        FLAT_CAPTURE_FAILURES.clear();
        BarStackBER.onResourceReload();
        ItemRenderOverrides.clearResolvedProfiles();
    }

    /**
     * One cube face worth drawing, carrying the transform that lays a quad's art on it and the
     * face's outward normal in the frame the vertices are written in.
     */
    private record FaceTarget(Direction face, Matrix4f art, float nx, float ny, float nz) {}

    /**
     * The cube faces the camera can see. The level renderer has already translated by the camera
     * position, so the camera sits at this frame's origin and a face is visible exactly when the
     * cell center lies on the face's inward side. The faces that fail the test are behind the
     * opaque background cube, so skipping them removes nothing that could be seen.
     */
    private static List<FaceTarget> visibleFaces(PoseStack pose, float scale, RenderOffset offset) {
        Matrix3f cellNormal = pose.last().normal();
        Vector3f centre = pose.last().pose().transformPosition(new Vector3f(0.5f, 0.5f, 0.5f));

        List<FaceTarget> faces = new ArrayList<>(3);
        for (Direction face : DIRECTIONS) {
            Vector3f normal = cellNormal.transform(
                    new Vector3f(face.getStepX(), face.getStepY(), face.getStepZ()));
            if (centre.dot(normal) >= 0.0f) {
                continue;
            }
            normal.normalize();
            faces.add(new FaceTarget(face, artMatrix(pose, face, scale, offset),
                    normal.x(), normal.y(), normal.z()));
        }
        return faces;
    }

    /**
     * The transform from a baked quad's own coordinates onto one cube face: turn the +Z face onto
     * the target face, sit just outside it keeping a sliver of the quad's own depth, then shrink the
     * art about the face center to leave a border and apply the profile's offset.
     */
    private static Matrix4f artMatrix(PoseStack pose, Direction face, float scale, RenderOffset offset) {
        pose.pushPose();

        Quaternionf rotation = FACE_ROTATIONS[face.ordinal()];
        if (rotation != null) {
            pose.translate(0.5f, 0.5f, 0.5f);
            pose.mulPose(rotation);
            pose.translate(-0.5f, -0.5f, -0.5f);
        }

        pose.translate(0.0f, 0.0f, 1.0f + FACE_OFFSET);
        pose.scale(1.0f, 1.0f, FACE_DEPTH);

        pose.translate(0.5f + offset.x(), 0.5f + offset.y(), 0.0f);
        pose.scale(ART_INSET * scale, ART_INSET * scale, 1.0f);
        pose.translate(-0.5f, -0.5f, 0.0f);

        Matrix4f art = new Matrix4f(pose.last().pose());
        pose.popPose();
        return art;
    }

    /**
     * Lays the item's plates onto each visible face. An item model lays its art on a front and a
     * back plate and hangs a sliver off every span of the sprite's outline. The slivers stand
     * perpendicular to the plates to give a held item its thickness; flattened onto a cell face they
     * are edge-on and cover nothing, so only quads facing along Z are drawn. Both plates are kept
     * and the render type's own back-face culling shows the outward one, exactly as it does for an
     * item in the hand.
     */
    private static void emitPlates(VertexConsumer vc, List<ItemCapture.TintedQuad> quads, int light,
                                   List<FaceTarget> faces) {
        for (ItemCapture.TintedQuad tinted : quads) {
            BakedQuad quad = tinted.quad();
            if (quad.getDirection().getAxis() != Direction.Axis.Z) {
                continue;
            }
            int color = tinted.color();
            int r = ARGB.red(color);
            int g = ARGB.green(color);
            int b = ARGB.blue(color);

            int[] vertices = quad.getVertices();
            for (FaceTarget target : faces) {
                for (int i = 0; i < 4; i++) {
                    int base = i * ItemCapture.BLOCK_VERTEX_STRIDE;
                    int position = base + ItemCapture.BLOCK_POSITION_OFFSET;
                    int uv = base + ItemCapture.BLOCK_UV_OFFSET;
                    vertex(vc, target.art(),
                            Float.intBitsToFloat(vertices[position]),
                            Float.intBitsToFloat(vertices[position + 1]),
                            Float.intBitsToFloat(vertices[position + 2]),
                            Float.intBitsToFloat(vertices[uv]),
                            Float.intBitsToFloat(vertices[uv + 1]),
                            r, g, b, light, target);
                }
            }
        }
    }

    private static void emitBackgroundFace(Matrix4f cell, VertexConsumer vc, TextureAtlasSprite sp, int light,
                                           FaceTarget target) {
        float[] c = FACE_CORNERS[target.face().ordinal()];
        float u0 = sp.getU0(), v0 = sp.getV0(), u1 = sp.getU1(), v1 = sp.getV1();

        vertex(vc, cell, c[0], c[1], c[2], u0, v1, 0xFF, 0xFF, 0xFF, light, target);
        vertex(vc, cell, c[3], c[4], c[5], u1, v1, 0xFF, 0xFF, 0xFF, light, target);
        vertex(vc, cell, c[6], c[7], c[8], u1, v0, 0xFF, 0xFF, 0xFF, light, target);
        vertex(vc, cell, c[9], c[10], c[11], u0, v0, 0xFF, 0xFF, 0xFF, light, target);
    }

    /**
     * Writes one vertex. The position is transformed here rather than through
     * {@link VertexConsumer#addVertex(Matrix4f, float, float, float)}, which allocates a vector per
     * call, and the normal is the cube face's rather than the source quad's, so lighting reads the
     * surface the art lies on instead of the direction of an extruded sprite edge.
     */
    private static void vertex(VertexConsumer vc, Matrix4f m, float x, float y, float z, float u, float v,
                               int r, int g, int b, int light, FaceTarget target) {
        vc.addVertex(m.m00() * x + m.m10() * y + m.m20() * z + m.m30(),
                        m.m01() * x + m.m11() * y + m.m21() * z + m.m31(),
                        m.m02() * x + m.m12() * y + m.m22() * z + m.m32())
                .setColor(r, g, b, 0xFF)
                .setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(light)
                .setNormal(target.nx(), target.ny(), target.nz());
    }
}
