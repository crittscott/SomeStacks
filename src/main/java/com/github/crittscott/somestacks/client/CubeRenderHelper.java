package com.github.crittscott.somestacks.client;

import com.github.crittscott.somestacks.SomeStacks;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
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
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HalfTransparentBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.StainedGlassPaneBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.registries.ForgeRegistries;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Draws one stored item inside one cell, in whichever way its render profile asks for. The Storage
 * and Singles renderers position a cell and delegate here; everything about how the item itself is
 * presented lives in this class.
 *
 * <p>The {@code 3d}, {@code gui}, and {@code block} modes hand off to Minecraft's own renderers.
 * The {@code 2d} mode is the one implemented here: flat item art projected onto the visible faces
 * of a small background cube, which is what makes a wall of stored items readable at a distance.
 */
public final class CubeRenderHelper {
    private CubeRenderHelper() {}

    public static final ResourceLocation STACK_CUBE_TEXTURE = new ResourceLocation("somestacks", "block/stack_cube");

    /** {@link Direction#values()} clones its array on every call, and this is a per-item loop. */
    private static final Direction[] DIRECTIONS = Direction.values();

    /** How far outside the cell face the projected art sits, clear of the background cube. */
    private static final float FACE_OFFSET = 0.001f;
    /** How much of a quad's own depth survives the projection, keeping a multi-pass item's layers apart. */
    private static final float FACE_DEPTH = 0.01f;
    /** Fraction of the face the art spans, leaving a one-pixel border. */
    private static final float ART_INSET = 0.875f;

    /**
     * Scale a stack block's renderer applies before handing a cell's contents here: an item's unit
     * cube becomes eight pixels.
     */
    public static final float CELL_RENDER_SCALE = 8.0f / 16.0f;

    /** One four-pixel cell in those local units, where 1.0 is the eight pixels above. */
    public static final float CELL_LOCAL_SIZE = 4.0f / 8.0f;

    /** The cell's centre in the same units, which a cell's contents are drawn about. */
    public static final float CELL_LOCAL_CENTRE = CELL_LOCAL_SIZE / 2.0f;

    /**
     * Rotation carrying the +Z face, where a baked item model lays its art out, onto each cube face.
     * All six are proper rotations, so the art reads the same way round from every side. SOUTH is
     * where the art already lies and needs none.
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

    /** Reseeded before every quad group, so one instance serves the whole render thread. */
    private static final RandomSource RANDOM = RandomSource.create();

    private static final BakedQuad[] NO_PLATES = new BakedQuad[0];

    /**
     * The quads of a render pass that survive the flat projection, keyed by the pass. Baked models
     * carry no equality beyond identity, which is the right key here anyway: a reload replaces every
     * instance, so an entry can only ever be read back for the pass it was gathered from.
     */
    private static final Map<BakedModel, BakedQuad[]> PLATE_CACHE = new IdentityHashMap<>();

    /**
     * Items whose block form threw when it was drawn. Held so the attempt is made once per bake
     * rather than once per frame for as long as the item is on screen: a renderer that throws
     * part way through a quad leaves the shared buffer mid-quad, and repeating that every frame
     * compounds it.
     */
    private static final Set<Item> BLOCK_RENDER_FAILURES = Collections.newSetFromMap(new IdentityHashMap<>());

    public static void renderItemInCube(ItemStack stack, PoseStack pose, MultiBufferSource buffers, int light,
                                        BlockRenderDispatcher blockRenderer, Level level) {
        RenderProfile profile = ItemRenderOverrides.resolve(stack);
        if (profile == null) {
            return;
        }
        float scale = profile.scale();
        float[] offset = profile.offset();

        switch (profile.mode()) {
            case TWO_D -> render2DItem(stack, pose, buffers, light, level, scale, offset);
            case THREE_D -> render3DItem(stack, pose, buffers, light, level, scale, offset);
            case BLOCK -> renderBlockItem(stack, pose, buffers, light, blockRenderer, level, scale, offset);
            case GUI -> renderGuiItem(stack, pose, buffers, light, level, scale, offset);
        }
    }

    private static void render3DItem(ItemStack stack, PoseStack pose, MultiBufferSource buffers, int light, Level level,
                                     float finalScale, float[] offset) {
        pose.pushPose();
        pose.scale(finalScale, finalScale, finalScale);
        pose.translate(CELL_LOCAL_CENTRE / finalScale, CELL_LOCAL_CENTRE / finalScale,
                CELL_LOCAL_CENTRE / finalScale);
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

    private static void renderGuiItem(ItemStack stack, PoseStack pose, MultiBufferSource buffers, int light, Level level,
                                      float finalScale, float[] offset) {
        pose.pushPose();
        pose.scale(finalScale, finalScale, finalScale);
        pose.translate(CELL_LOCAL_CENTRE / finalScale, CELL_LOCAL_CENTRE / finalScale,
                CELL_LOCAL_CENTRE / finalScale);
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

    private static void render2DItem(ItemStack stack, PoseStack pose, MultiBufferSource buffers, int light, Level level,
                                     float scale, float[] offset) {
        pose.pushPose();
        pose.scale(CELL_LOCAL_SIZE, CELL_LOCAL_SIZE, CELL_LOCAL_SIZE);
        BakedModel model = Minecraft.getInstance().getItemRenderer().getModel(stack, level, null, 0);
        render2DItemCube(pose, buffers, stack, model, light, scale, offset);
        pose.popPose();
    }

    private static void renderBlockItem(ItemStack stack, PoseStack pose, MultiBufferSource buffers, int light,
                                        BlockRenderDispatcher blockRenderer, Level level,
                                        float finalScale, float[] offset) {
        if (!(stack.getItem() instanceof BlockItem blockItem)) {
            // Fallback if someone misconfigured a non-BlockItem as "block" mode
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
        pose.translate(offset[0] / finalScale, offset[1] / finalScale, offset[2] / finalScale);
        pose.mulPose(Axis.YP.rotationDegrees(180));
        pose.translate(-0.5, -0.5, -0.5);

        try {
            blockRenderer.renderSingleBlock(blockState, pose, buffers, light, OverlayTexture.NO_OVERLAY);
        } catch (Exception e) {
            // A third-party block model that cannot be baked or drawn on its own. The model path
            // works from the pose it is handed and never pushes, so the stack is where we left it.
            pose.popPose();
            if (BLOCK_RENDER_FAILURES.add(stack.getItem())) {
                SomeStacks.LOGGER.warn("Block rendering threw for {}; drawing it as 3d instead",
                        ForgeRegistries.ITEMS.getKey(stack.getItem()), e);
            }
            render3DItem(stack, pose, buffers, light, level, finalScale, offset);
            return;
        }

        pose.popPose();
    }

    /**
     * The flag {@code ItemRenderer.render} passes to {@link BakedModel#getRenderPasses}, which
     * selects a model's passes and their render types: false only for the translucent blocks
     * vanilla draws through the indirect buffers outside GUI and first-person contexts.
     */
    public static boolean fabulousFlag(ItemStack stack, ItemDisplayContext context) {
        if (context == ItemDisplayContext.GUI || context.firstPerson()
                || !(stack.getItem() instanceof BlockItem blockItem)) {
            return true;
        }
        Block block = blockItem.getBlock();
        return !(block instanceof HalfTransparentBlock) && !(block instanceof StainedGlassPaneBlock);
    }

    private static void render2DItemCube(PoseStack pose, MultiBufferSource buffers, ItemStack stack, BakedModel model, int light,
                                         float scale, float[] offset) {
        List<FaceTarget> faces = visibleFaces(pose, scale, offset);
        if (faces.isEmpty()) {
            return;
        }

        TextureAtlasSprite backgroundSprite = Minecraft.getInstance()
                .getTextureAtlas(InventoryMenu.BLOCK_ATLAS)
                .apply(STACK_CUBE_TEXTURE);
        VertexConsumer solidVc = buffers.getBuffer(RenderType.solid());
        for (FaceTarget target : faces) {
            emitBackgroundFace(pose.last().pose(), solidVc, backgroundSprite, light, target);
        }

        VertexConsumer vc = buffers.getBuffer(RenderType.cutout());
        boolean fabulous = fabulousFlag(stack, ItemDisplayContext.FIXED);

        // Resolving the model and its passes stays here, so a stack's own state still chooses both:
        // a charged crossbow draws charged, a coated weapon draws its coating. Only the step from a
        // pass to the quads worth drawing depends on nothing but the pass, and that one is cached.
        List<BakedModel> passes = model.getRenderPasses(stack, fabulous);
        for (int i = 0; i < passes.size(); i++) {
            emitQuads(vc, stack, plates(passes.get(i)), light, faces);
        }
    }

    private static BakedQuad[] plates(BakedModel pass) {
        BakedQuad[] cached = PLATE_CACHE.get(pass);
        if (cached == null) {
            cached = gatherPlates(pass);
            PLATE_CACHE.put(pass, cached);
        }
        return cached;
    }

    /**
     * Collects the quads of one pass that the flat projection can show. An item model lays its art on
     * a front and a back plate and hangs a sliver off every span of the sprite's outline. The slivers
     * stand perpendicular to the plates to give a held item its thickness; flattened onto a cell face
     * they are edge-on and cover nothing, and they outnumber the plates by up to a hundred to one.
     */
    private static BakedQuad[] gatherPlates(BakedModel pass) {
        // Walk the pass as ItemRenderer.renderModelLists does: each culled direction group and then
        // the unculled group, reseeding per group.
        List<BakedQuad> plates = new ArrayList<>(2);
        for (Direction bucket : DIRECTIONS) {
            RANDOM.setSeed(42L);
            collectPlates(pass.getQuads(null, bucket, RANDOM), plates);
        }
        RANDOM.setSeed(42L);
        collectPlates(pass.getQuads(null, null, RANDOM), plates);
        return plates.isEmpty() ? NO_PLATES : plates.toArray(NO_PLATES);
    }

    private static void collectPlates(List<BakedQuad> quads, List<BakedQuad> plates) {
        for (BakedQuad quad : quads) {
            if (quad.getDirection().getAxis() == Direction.Axis.Z) {
                plates.add(quad);
            }
        }
    }

    /** Drops what was gathered or learned from the baked models a reload is about to replace. */
    public static void onResourceReload() {
        PLATE_CACHE.clear();
        BLOCK_RENDER_FAILURES.clear();
    }

    /**
     * One cube face worth drawing, carrying the transform that lays a quad's art on it and the
     * face's outward normal in the frame the vertices are written in.
     */
    private record FaceTarget(Direction face, Matrix4f art, float nx, float ny, float nz) {}

    /**
     * The cube faces the camera can see. The level renderer has already translated by the camera
     * position, so the camera sits at this frame's origin and a face is visible exactly when the
     * cell centre lies on the face's inward side. The faces that fail the test are behind the
     * opaque background cube, so skipping them removes nothing that could be seen.
     */
    private static List<FaceTarget> visibleFaces(PoseStack pose, float scale, float[] offset) {
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
     * art about the face centre to leave a border and apply the profile's offset.
     */
    private static Matrix4f artMatrix(PoseStack pose, Direction face, float scale, float[] offset) {
        pose.pushPose();

        Quaternionf rotation = FACE_ROTATIONS[face.ordinal()];
        if (rotation != null) {
            pose.translate(0.5f, 0.5f, 0.5f);
            pose.mulPose(rotation);
            pose.translate(-0.5f, -0.5f, -0.5f);
        }

        pose.translate(0.0f, 0.0f, 1.0f + FACE_OFFSET);
        pose.scale(1.0f, 1.0f, FACE_DEPTH);

        pose.translate(0.5f + offset[0], 0.5f + offset[1], 0.0f);
        pose.scale(ART_INSET * scale, ART_INSET * scale, 1.0f);
        pose.translate(-0.5f, -0.5f, 0.0f);

        Matrix4f art = new Matrix4f(pose.last().pose());
        pose.popPose();
        return art;
    }

    private static void emitQuads(VertexConsumer vc, ItemStack stack, BakedQuad[] plates, int light,
                                  List<FaceTarget> faces) {
        int tintIndex = Integer.MIN_VALUE;
        int r = 0xFF, g = 0xFF, b = 0xFF;

        // Both plates are kept and the render type's own back-face culling shows the outward one,
        // exactly as it does for an item in the hand.
        for (BakedQuad quad : plates) {
            // Every quad of a layer carries that layer's tint index, so a pass resolves its colour
            // once rather than once per quad. Colour cannot be cached with the geometry: a potion's
            // and a coated weapon's are per stack.
            if (quad.getTintIndex() != tintIndex) {
                tintIndex = quad.getTintIndex();
                int color = tintIndex >= 0
                        ? Minecraft.getInstance().getItemColors().getColor(stack, tintIndex)
                        : 0xFFFFFFFF;
                r = (color >> 16) & 0xFF;
                g = (color >> 8) & 0xFF;
                b = color & 0xFF;
            }

            int[] vertices = quad.getVertices();
            for (FaceTarget target : faces) {
                for (int i = 0; i < 4; i++) {
                    int base = i * 8;
                    vertex(vc, target.art(),
                            Float.intBitsToFloat(vertices[base]),
                            Float.intBitsToFloat(vertices[base + 1]),
                            Float.intBitsToFloat(vertices[base + 2]),
                            Float.intBitsToFloat(vertices[base + 4]),
                            Float.intBitsToFloat(vertices[base + 5]),
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
     * {@link VertexConsumer#vertex(Matrix4f, float, float, float)}, which allocates a vector per
     * call, and the normal is the cube face's rather than the source quad's, so lighting reads the
     * surface the art lies on instead of the direction of an extruded sprite edge.
     */
    private static void vertex(VertexConsumer vc, Matrix4f m, float x, float y, float z, float u, float v,
                               int r, int g, int b, int light, FaceTarget target) {
        vc.vertex(m.m00() * x + m.m10() * y + m.m20() * z + m.m30(),
                        m.m01() * x + m.m11() * y + m.m21() * z + m.m31(),
                        m.m02() * x + m.m12() * y + m.m22() * z + m.m32())
                .color(r, g, b, 0xFF)
                .uv(u, v)
                .overlayCoords(OverlayTexture.NO_OVERLAY)
                .uv2(light)
                .normal(target.nx(), target.ny(), target.nz())
                .endVertex();
    }
}
