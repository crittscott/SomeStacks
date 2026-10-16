package com.github.crittscott.somestacks.client;

import com.github.crittscott.somestacks.SomeStacksCommon;
import com.github.crittscott.somestacks.util.CubeGrid;
import com.github.crittscott.somestacks.util.QuarterTurns;
import com.github.crittscott.somestacks.util.SlotAccess;
import com.github.crittscott.somestacks.renderconfig.RenderOffset;
import com.github.crittscott.somestacks.renderconfig.RenderMode;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.model.geom.builders.UVPair;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.data.AtlasIds;
import net.minecraft.core.Direction;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.joml.Vector3fc;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
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

    public static final Identifier STACK_CUBE_TEXTURE = Identifier.fromNamespaceAndPath(SomeStacksCommon.MODID, "block/stack_cube");
    /** The {@code 2d} background drawn without art for an item whose model threw when captured. */
    public static final Identifier STACK_CUBE_ERROR_TEXTURE =
            Identifier.fromNamespaceAndPath(SomeStacksCommon.MODID, "block/stack_cube_error");
    public static final Identifier RENDER_CACHE_RELOAD_LISTENER_ID =
            Identifier.fromNamespaceAndPath(SomeStacksCommon.MODID, "render_caches");

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

    /** A grid snapshot whose cells contain only prepared presentation data. */
    public static final class GridRenderState extends BlockEntityRenderState {
        private List<CellRenderState> cells = List.of();
        private float scale;
        private int light;
        private Vec3 cameraInBlock = Vec3.ZERO;
    }

    private record PreparedItem(RenderProfile profile, @Nullable ItemStackRenderState item,
                                @Nullable BlockState block, List<PlateBatch> plates,
                                @Nullable TextureAtlasSprite background) {}

    /** Flat-art quads sharing the render type, and so the atlas, their item submitted them with. */
    private record PlateBatch(RenderType renderType, List<BakedQuad> quads, List<Integer> colors) {}

    private record CellRenderState(AABB bounds, int quarterTurns, PreparedItem item) {}

    private record CaptureKey(Item item, DataComponentPatch components, int count) {
        static CaptureKey of(ItemStack stack) {
            return new CaptureKey(stack.getItem(), stack.getComponentsPatch(), stack.getCount());
        }
    }

    private static final Set<Item> FLAT_CAPTURE_FAILURES = Collections.newSetFromMap(new IdentityHashMap<>());

    /** Extracts inventories, profiles, item models, textures, and lighting before submission. */
    public static void extractGridItems(
            SlotAccess items, int slots, CubeGrid grid, int blockRotation,
            float renderScale, IntUnaryOperator itemRotation,
            @Nullable Level level, BlockPos blockPos, Vec3 cameraPos, GridRenderState state) {
        state.cells = List.of();
        if (level == null) {
            return;
        }
        state.scale = renderScale;
        state.light = LevelRenderer.getLightColor(level, blockPos);
        state.cameraInBlock = cameraPos.subtract(blockPos.getX(), blockPos.getY(), blockPos.getZ());
        Map<CaptureKey, PreparedItem> prepared = new HashMap<>();
        List<CellRenderState> cells = new ArrayList<>();
        for (int index = 0; index < slots; index++) {
            ItemStack stack = items.getStackInSlot(index);
            if (stack.isEmpty()) {
                continue;
            }
            PreparedItem item = prepared.computeIfAbsent(CaptureKey.of(stack),
                    ignored -> prepareItem(stack.copy(), level));
            if (item != null) {
                cells.add(new CellRenderState(grid.localBox(index, blockRotation),
                        itemRotation.applyAsInt(index), item));
            }
        }
        state.cells = List.copyOf(cells);
    }

    @Nullable
    private static PreparedItem prepareItem(ItemStack stack, Level level) {
        RenderProfile profile = ItemRenderOverrides.resolve(stack);
        if (profile == null) {
            return null;
        }
        if (profile.mode() == RenderMode.TWO_D) {
            List<ItemCapture.TintedQuad> quads = captureFlatArt(stack, level);
            Identifier background = quads == null ? STACK_CUBE_ERROR_TEXTURE : STACK_CUBE_TEXTURE;
            TextureAtlasSprite sprite = Minecraft.getInstance()
                    .getAtlasManager().getAtlasOrThrow(AtlasIds.BLOCKS).getSprite(background);
            return new PreparedItem(profile, null, null, quads == null ? List.of() : plateBatches(quads), sprite);
        }
        if (profile.mode() == RenderMode.BLOCK
                && stack.getItem() instanceof BlockItem blockItem) {
            BlockState block = blockItem.getBlock().defaultBlockState();
            if (block.getRenderShape() == RenderShape.MODEL) {
                return new PreparedItem(profile, null, block, List.of(), null);
            }
        }
        ItemStackRenderState item = new ItemStackRenderState();
        ItemDisplayContext context = profile.mode() == RenderMode.GUI
                ? ItemDisplayContext.GUI : ItemDisplayContext.FIXED;
        Minecraft.getInstance().getItemModelResolver().updateForTopItem(item, stack, context, level, null, 0);
        return new PreparedItem(profile, item, null, List.of(), null);
    }

    /** Submits prepared cells without consulting the level or inventories. */
    public static void submitGridItems(GridRenderState state, PoseStack pose, SubmitNodeCollector collector) {
        PoseStack localPose = new PoseStack();
        for (CellRenderState cell : state.cells) {
            pose.pushPose();
            localPose.pushPose();
            try {
                placeCell(pose, cell, state.scale);
                placeCell(localPose, cell, state.scale);
                submitItemInCube(cell.item(), pose, localPose, state.cameraInBlock, collector, state.light);
            } finally {
                localPose.popPose();
                pose.popPose();
            }
        }
    }

    private static void placeCell(PoseStack pose, CellRenderState cell, float scale) {
        pose.translate(cell.bounds().minX, cell.bounds().minY, cell.bounds().minZ);
        pose.scale(scale, scale, scale);
        if (cell.quarterTurns() != 0) {
            pose.translate(CELL_LOCAL_CENTRE, CELL_LOCAL_CENTRE, CELL_LOCAL_CENTRE);
            pose.mulPose(Axis.YP.rotationDegrees(QuarterTurns.degrees(cell.quarterTurns())));
            pose.translate(-CELL_LOCAL_CENTRE, -CELL_LOCAL_CENTRE, -CELL_LOCAL_CENTRE);
        }
    }

    private static void submitItemInCube(PreparedItem prepared, PoseStack pose, PoseStack localPose,
                                         Vec3 cameraInBlock, SubmitNodeCollector collector, int light) {
        RenderProfile profile = prepared.profile();
        if (profile.mode() == RenderMode.TWO_D) {
            submit2DItem(prepared, pose, localPose, cameraInBlock, collector, light);
            return;
        }
        float scale = profile.scale();
        RenderOffset offset = profile.offset();
        pose.pushPose();
        try {
            pose.scale(scale, scale, scale);
            pose.translate(CELL_LOCAL_CENTRE / scale, CELL_LOCAL_CENTRE / scale, CELL_LOCAL_CENTRE / scale);
            pose.translate(offset.x() / scale, offset.y() / scale, offset.z() / scale);
            if (prepared.block() != null) {
                pose.mulPose(Axis.YP.rotationDegrees(180));
                pose.translate(-0.5, -0.5, -0.5);
                collector.submitBlock(pose, prepared.block(), light, OverlayTexture.NO_OVERLAY, 0);
            } else if (prepared.item() != null) {
                if (profile.mode() == RenderMode.GUI) {
                    applyGuiCounterRotation(pose);
                }
                prepared.item().submit(pose, collector, light, OverlayTexture.NO_OVERLAY, 0);
            }
        } finally {
            pose.popPose();
        }
    }

    /** Applies the counter-rotation shared by GUI submission and automatic GUI measurement. */
    public static void applyGuiCounterRotation(PoseStack pose) {
        pose.mulPose(Axis.YP.rotationDegrees(-45.0f));
        pose.mulPose(Axis.XP.rotationDegrees(-30.0f));
    }

    @Nullable
    private static List<ItemCapture.TintedQuad> captureFlatArt(ItemStack stack, Level level) {
        Item item = stack.getItem();
        if (FLAT_CAPTURE_FAILURES.contains(item)) {
            return null;
        }
        try {
            return ItemCapture.captureQuads(stack, ItemDisplayContext.FIXED, level, new PoseStack()).quads();
        } catch (RuntimeException e) {
            FLAT_CAPTURE_FAILURES.add(item);
            SomeStacksCommon.LOGGER.warn("Item model threw while capturing 2d art for {}; drawing the error cube instead",
                    BuiltInRegistries.ITEM.getKey(item), e);
            return null;
        }
    }

    private static void placeFlatCube(PoseStack pose) {
        pose.scale(CELL_LOCAL_SIZE, CELL_LOCAL_SIZE, CELL_LOCAL_SIZE);
        pose.translate(0.5f, 0.5f, 0.5f);
        pose.scale(CUBE_INSET, CUBE_INSET, CUBE_INSET);
        pose.translate(-0.5f, -0.5f, -0.5f);
    }

    private static void submit2DItem(PreparedItem prepared, PoseStack pose, PoseStack localPose,
                                     Vec3 cameraInBlock, SubmitNodeCollector collector, int light) {
        pose.pushPose();
        localPose.pushPose();
        try {
            placeFlatCube(pose);
            placeFlatCube(localPose);
            RenderProfile profile = prepared.profile();
            List<FaceTarget> faces = visibleFaces(pose, localPose, cameraInBlock, profile.scale(), profile.offset());
            if (faces.isEmpty()) {
                return;
            }
            Matrix4f cell = new Matrix4f(pose.last().pose());
            TextureAtlasSprite sprite = prepared.background();
            collector.submitCustomGeometry(pose, RenderTypes.solidMovingBlock(), (snapshot, vc) -> {
                for (FaceTarget target : faces) {
                    emitBackgroundFace(cell, vc, sprite, light, target);
                }
            });
            for (PlateBatch batch : prepared.plates()) {
                collector.submitCustomGeometry(pose, batch.renderType(),
                        (snapshot, vc) -> emitPlates(vc, batch, light, faces));
            }
        } finally {
            localPose.popPose();
            pose.popPose();
        }
    }

    /** Clears model-dependent failure state and sprite/profile caches on resource reload. */
    public static void onResourceReload() {
        FLAT_CAPTURE_FAILURES.clear();
        BarStackBER.onResourceReload();
        ItemRenderOverrides.clearResolvedProfiles();
    }

    /** One cube face worth drawing, carrying the transform that lays a quad's art on it. */
    private record FaceTarget(Direction face, Matrix4f art) {}

    /** Selects faces in block-local space and prepares their submission-space transforms. */
    private static List<FaceTarget> visibleFaces(PoseStack pose, PoseStack localPose, Vec3 cameraInBlock,
                                                 float scale, RenderOffset offset) {
        Matrix3f localNormal = localPose.last().normal();
        Vector3f centre = localPose.last().pose().transformPosition(new Vector3f(0.5f, 0.5f, 0.5f));
        centre.sub((float) cameraInBlock.x, (float) cameraInBlock.y, (float) cameraInBlock.z);
        List<FaceTarget> faces = new ArrayList<>(3);
        for (Direction face : DIRECTIONS) {
            Vector3f facing = localNormal.transform(
                    new Vector3f(face.getStepX(), face.getStepY(), face.getStepZ()));
            if (centre.dot(facing) >= 0.0f) {
                continue;
            }
            faces.add(new FaceTarget(face, artMatrix(pose, face, scale, offset)));
        }
        return List.copyOf(faces);
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
     * Groups an item's plates by render type, keeping submission order. An item model lays its art
     * on a front and a back plate and hangs a sliver off every span of the sprite's outline. The
     * slivers stand perpendicular to the plates to give a held item its thickness; flattened onto a
     * cell face they are edge-on and cover nothing, so only quads facing along Z are kept. Both
     * plates are kept and the render type's own back-face culling shows the outward one, exactly as
     * it does for an item in the hand.
     */
    private static List<PlateBatch> plateBatches(List<ItemCapture.TintedQuad> quads) {
        Map<RenderType, PlateBatch> batches = new LinkedHashMap<>();
        for (ItemCapture.TintedQuad tinted : quads) {
            if (tinted.quad().direction().getAxis() != Direction.Axis.Z) {
                continue;
            }
            PlateBatch batch = batches.computeIfAbsent(tinted.renderType(),
                    type -> new PlateBatch(type, new ArrayList<>(), new ArrayList<>()));
            batch.quads().add(tinted.quad());
            batch.colors().add(tinted.color());
        }
        List<PlateBatch> result = new ArrayList<>(batches.size());
        for (PlateBatch batch : batches.values()) {
            result.add(new PlateBatch(batch.renderType(), List.copyOf(batch.quads()),
                    List.copyOf(batch.colors())));
        }
        return List.copyOf(result);
    }

    /** Lays one batch of the item's plates onto each visible face. */
    private static void emitPlates(VertexConsumer vc, PlateBatch batch, int light, List<FaceTarget> faces) {
        for (int q = 0; q < batch.quads().size(); q++) {
            BakedQuad quad = batch.quads().get(q);
            int color = batch.colors().get(q);
            int r = ARGB.red(color);
            int g = ARGB.green(color);
            int b = ARGB.blue(color);

            for (FaceTarget target : faces) {
                for (int i = 0; i < BakedQuad.VERTEX_COUNT; i++) {
                    Vector3fc position = quad.position(i);
                    long uv = quad.packedUV(i);
                    vertex(vc, target.art(), position.x(), position.y(), position.z(),
                            UVPair.unpackU(uv), UVPair.unpackV(uv), r, g, b, light);
                }
            }
        }
    }

    private static void emitBackgroundFace(Matrix4f cell, VertexConsumer vc, TextureAtlasSprite sp, int light,
                                           FaceTarget target) {
        float[] c = FACE_CORNERS[target.face().ordinal()];
        float u0 = sp.getU0(), v0 = sp.getV0(), u1 = sp.getU1(), v1 = sp.getV1();

        vertex(vc, cell, c[0], c[1], c[2], u0, v1, 0xFF, 0xFF, 0xFF, light);
        vertex(vc, cell, c[3], c[4], c[5], u1, v1, 0xFF, 0xFF, 0xFF, light);
        vertex(vc, cell, c[6], c[7], c[8], u1, v0, 0xFF, 0xFF, 0xFF, light);
        vertex(vc, cell, c[9], c[10], c[11], u0, v0, 0xFF, 0xFF, 0xFF, light);
    }

    /**
     * Writes one vertex. The position is transformed here rather than through
     * {@link VertexConsumer#addVertex(Matrix4f, float, float, float)}, which allocates a vector per
     * call. Every vertex carries a straight-up normal: the plates draw with the item's own render
     * type, whose shader shades by normal, and an upward normal lights every face fully, so all
     * faces of a cube match each other and the unshaded background.
     */
    private static void vertex(VertexConsumer vc, Matrix4f m, float x, float y, float z, float u, float v,
                               int r, int g, int b, int light) {
        vc.addVertex(m.m00() * x + m.m10() * y + m.m20() * z + m.m30(),
                        m.m01() * x + m.m11() * y + m.m21() * z + m.m31(),
                        m.m02() * x + m.m12() * y + m.m22() * z + m.m32())
                .setColor(r, g, b, 0xFF)
                .setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(light)
                .setNormal(0.0f, 1.0f, 0.0f);
    }
}
