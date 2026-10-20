package com.github.crittscott.somestacks.client;

import com.github.crittscott.somestacks.SomeStacksCommon;
import com.github.crittscott.somestacks.renderconfig.RenderMode;
import com.github.crittscott.somestacks.renderconfig.RenderOffset;
import com.github.crittscott.somestacks.util.CubeGrid;
import com.github.crittscott.somestacks.util.QuarterTurns;
import com.github.crittscott.somestacks.util.SinglesCubeIdx;
import com.github.crittscott.somestacks.util.SlotAccess;
import com.github.crittscott.somestacks.util.StorageCubeIdx;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.builders.UVPair;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.block.BlockModelRenderState;
import net.minecraft.client.renderer.block.BlockModelResolver;
import net.minecraft.client.renderer.block.model.BlockDisplayContext;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.data.AtlasIds;
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
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.joml.Vector3fc;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.IntUnaryOperator;
import javax.annotation.Nullable;

/**
 * Draws the stored items of a Storage or Singles grid according to their render profiles. The
 * Storage and Singles renderers supply the grid geometry and delegate here; everything about how
 * an item itself is presented lives in this class.
 *
 * <p>The {@code 3d}, {@code gui}, and {@code block} modes hand off to Minecraft's own renderers.
 * The {@code 2d} mode is the one implemented here: flat item art projected onto the visible faces
 * of a small background cube, which is what makes a wall of stored items readable at a distance.
 *
 * <p>Work is done when something changes. A block entity's cells, profiles, resolved models, and
 * cell transforms are kept in a {@link GridCache} until its contents, layout rotation, or profiles
 * change; a frame only selects visible faces and submits. {@code 2d} art is captured once per
 * distinct stack and shared by every block showing it. All {@code 2d} cells of a block go out in
 * one submission per render type.
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
    public static final float STORAGE_CELL_RENDER_SCALE = CELL_RENDER_SCALE
            * (float) StorageCubeIdx.CELL_PIXELS / (float) SinglesCubeIdx.CELL_PIXELS;

    /** One four-pixel cell in those local units, where 1.0 is the eight pixels above. */
    public static final float CELL_LOCAL_SIZE = (float) SinglesCubeIdx.CELL_PIXELS / 8.0f;

    /** The cell's center in the same units, which a cell's contents are drawn about. */
    public static final float CELL_LOCAL_CENTRE = CELL_LOCAL_SIZE / 2.0f;

    /**
     * Rotations mapping the baked model's +Z art plane onto each cube face. All are proper rotations,
     * preserving the art's orientation. SOUTH already matches the source plane and needs none.
     */
    private static final Quaternionf[] FACE_ROTATIONS = new Quaternionf[DIRECTIONS.length];

    /** Corners of each cube face in cell space, wound to face outward. */
    private static final float[][] FACE_CORNERS = new float[DIRECTIONS.length][];

    /**
     * Outward normal of each cell-space cube face in block space, per item quarter turn. Cells are
     * only translated, uniformly scaled, and turned about Y, so these decide face visibility.
     */
    private static final Vector3f[][] FACE_NORMALS = new Vector3f[QuarterTurns.COUNT][DIRECTIONS.length];

    /** Cell space to the {@code 2d} background cube, inset so neighboring cubes stay apart. */
    private static final Matrix4fc FLAT_CUBE = new Matrix4f()
            .scale(CELL_LOCAL_SIZE)
            .translate(0.5f, 0.5f, 0.5f)
            .scale(CUBE_INSET)
            .translate(-0.5f, -0.5f, -0.5f);

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

        for (int turns = 0; turns < QuarterTurns.COUNT; turns++) {
            Quaternionf turn = Axis.YP.rotationDegrees(QuarterTurns.degrees(turns));
            for (Direction face : DIRECTIONS) {
                FACE_NORMALS[turns][face.ordinal()] = turn.transform(
                        new Vector3f(face.getStepX(), face.getStepY(), face.getStepZ()));
            }
        }
    }

    /** One frame's prepared grid; submission never reads the level or inventory. */
    public static final class GridRenderState extends BlockEntityRenderState {
        private Frame frame = Frame.EMPTY;
    }

    /**
     * A block entity's prepared cells. Everything here depends only on its contents, layout
     * rotation, and the current render profiles; the block entity holds it between frames and a
     * client update discards it.
     */
    public static final class GridCache {
        private final int blockRotation;
        private final int profileGeneration;
        private final float scale;
        private final Entry[] entries;
        private final Cell[] cells;
        /** The distinct render types of every {@code 2d} entry's plates, in first-seen order. */
        private final List<RenderType> plateTypes;

        private GridCache(int blockRotation, float scale, Entry[] entries, Cell[] cells,
                          List<RenderType> plateTypes) {
            this.blockRotation = blockRotation;
            this.profileGeneration = ItemRenderOverrides.generation();
            this.scale = scale;
            this.entries = entries;
            this.cells = cells;
            this.plateTypes = plateTypes;
        }

        private boolean isCurrent(int blockRotation) {
            return this.blockRotation == blockRotation
                    && profileGeneration == ItemRenderOverrides.generation();
        }
    }

    /**
     * One distinct stored stack, resolved once when its grid cache is built. A {@code 2d} entry
     * carries its art and the transform onto each cube face; a {@code block} entry its block model;
     * a {@code 3d} or {@code gui} entry its item model. A model that changes with time, such as a
     * compass, shows its state from when the cache was built.
     */
    private record Entry(RenderProfile profile, @Nullable FlatArt art, @Nullable Matrix4f[] faceArt,
                         @Nullable BlockModelRenderState block, @Nullable ItemStackRenderState item) {}

    /**
     * One occupied cell: its block-local placement, and for {@code 2d} its background cube's
     * transform and center in block space.
     */
    private record Cell(int entry, AABB bounds, int quarterTurns, Matrix4f flatCube,
                        float centreX, float centreY, float centreZ) {}

    /**
     * A frame's grid, light, and per-cell visible {@code 2d} faces, as a bit per {@link Direction}
     * ordinal. Immutable once built, so deferred submission sees this frame only.
     */
    private record Frame(@Nullable GridCache cache, int[] faces, int light) {
        static final Frame EMPTY = new Frame(null, new int[0], 0);
    }

    /** The {@code 2d} art of one rendered model: its plates, and the background they sit on. */
    private record FlatArt(List<PlateBatch> plates, TextureAtlasSprite background) {}

    /** Flat-art quads sharing the render type, and so the atlas, their item submitted them with. */
    private record PlateBatch(RenderType renderType, List<BakedQuad> quads, List<Integer> colors) {}

    private record CaptureKey(Item item, DataComponentPatch components, int count) {
        static CaptureKey of(ItemStack stack) {
            return new CaptureKey(stack.getItem(), stack.getComponentsPatch(), stack.getCount());
        }
    }

    private static final Set<Item> FLAT_CAPTURE_FAILURES = Collections.newSetFromMap(new IdentityHashMap<>());

    /** Maximum number of component/count-distinct {@code 2d} captures retained across all grids. */
    private static final int MAX_FLAT_ART_ITEMS = 2048;

    /**
     * {@code 2d} art by stack identity, shared by every grid showing that stack, in access order.
     * The least recently shown capture is evicted above {@link #MAX_FLAT_ART_ITEMS}; reload clears all.
     */
    private static final Map<CaptureKey, FlatArt> FLAT_ART = new LinkedHashMap<>(16, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<CaptureKey, FlatArt> eldest) {
            return size() > MAX_FLAT_ART_ITEMS;
        }
    };

    @Nullable
    private static FlatArt errorArt;

    /**
     * Prepares one frame of a grid. {@code previous} is what this call returned last time for the
     * same block entity, or null; it is reused while still current.
     *
     * @return the grid cache to hand back on the next frame.
     */
    @Nullable
    public static GridCache extractGridItems(
            @Nullable GridCache previous, SlotAccess items, int slots, CubeGrid grid, int blockRotation,
            float renderScale, IntUnaryOperator itemRotation, BlockModelResolver blockModels,
            @Nullable Level level, BlockPos blockPos, Vec3 cameraPos, GridRenderState state) {
        state.frame = Frame.EMPTY;
        if (level == null) {
            return previous;
        }
        GridCache cache = previous != null && previous.isCurrent(blockRotation) ? previous
                : buildCache(items, slots, grid, blockRotation, renderScale, itemRotation, blockModels, level);
        if (cache.cells.length == 0) {
            return cache;
        }

        float cameraX = (float) (cameraPos.x - blockPos.getX());
        float cameraY = (float) (cameraPos.y - blockPos.getY());
        float cameraZ = (float) (cameraPos.z - blockPos.getZ());
        int[] faces = new int[cache.cells.length];
        for (int c = 0; c < cache.cells.length; c++) {
            Cell cell = cache.cells[c];
            if (cache.entries[cell.entry()].art() != null) {
                faces[c] = visibleFaces(cell, cameraX, cameraY, cameraZ);
            }
        }
        state.frame = new Frame(cache, faces, state.lightCoords);
        return cache;
    }

    private static GridCache buildCache(SlotAccess items, int slots, CubeGrid grid, int blockRotation,
                                        float renderScale, IntUnaryOperator itemRotation,
                                        BlockModelResolver blockModels, Level level) {
        Map<CaptureKey, Integer> entryIndex = new HashMap<>();
        List<Entry> entries = new ArrayList<>();
        List<Cell> cells = new ArrayList<>();
        List<RenderType> plateTypes = new ArrayList<>(1);
        for (int index = 0; index < slots; index++) {
            ItemStack stack = items.getStackInSlot(index);
            if (stack.isEmpty()) {
                continue;
            }
            CaptureKey key = CaptureKey.of(stack);
            Integer entry = entryIndex.get(key);
            if (entry == null) {
                Entry prepared = prepareEntry(stack.copy(), key, blockModels, level);
                if (prepared == null) {
                    continue;
                }
                if (prepared.art() != null) {
                    for (PlateBatch batch : prepared.art().plates()) {
                        if (!plateTypes.contains(batch.renderType())) {
                            plateTypes.add(batch.renderType());
                        }
                    }
                }
                entry = entries.size();
                entries.add(prepared);
                entryIndex.put(key, entry);
            }
            AABB bounds = grid.localBox(index, blockRotation);
            int turns = QuarterTurns.normalize(itemRotation.applyAsInt(index));
            Matrix4f flatCube = cellMatrix(bounds, turns, renderScale).mul(FLAT_CUBE);
            Vector3f centre = flatCube.transformPosition(new Vector3f(0.5f));
            cells.add(new Cell(entry, bounds, turns, flatCube, centre.x(), centre.y(), centre.z()));
        }
        return new GridCache(blockRotation, renderScale,
                entries.toArray(Entry[]::new), cells.toArray(Cell[]::new), List.copyOf(plateTypes));
    }

    @Nullable
    private static Entry prepareEntry(ItemStack stack, CaptureKey key, BlockModelResolver blockModels,
                                      Level level) {
        RenderProfile profile = ItemRenderOverrides.resolve(stack);
        if (profile == null) {
            return null;
        }
        if (profile.mode() == RenderMode.TWO_D) {
            Matrix4f[] faceArt = new Matrix4f[DIRECTIONS.length];
            for (Direction face : DIRECTIONS) {
                faceArt[face.ordinal()] = artMatrix(face, profile.scale(), profile.offset());
            }
            return new Entry(profile, flatArt(stack, key, level), faceArt, null, null);
        }
        if (profile.mode() == RenderMode.BLOCK
                && stack.getItem() instanceof BlockItem blockItem) {
            BlockState block = blockItem.getBlock().defaultBlockState();
            if (block.getRenderShape() == RenderShape.MODEL) {
                BlockModelRenderState model = new BlockModelRenderState();
                blockModels.update(model, block, BlockDisplayContext.create());
                return new Entry(profile, null, null, model, null);
            }
        }
        ItemStackRenderState item = new ItemStackRenderState();
        ItemDisplayContext context = profile.mode() == RenderMode.GUI
                ? ItemDisplayContext.GUI : ItemDisplayContext.FIXED;
        Minecraft.getInstance().getItemModelResolver().updateForTopItem(item, stack, context, level, null, 0);
        return new Entry(profile, null, null, null, item);
    }

    /** A cell's block-local transform: its corner, the cell-render scale, and the item's own turn. */
    private static Matrix4f cellMatrix(AABB bounds, int quarterTurns, float scale) {
        Matrix4f matrix = new Matrix4f()
                .translate((float) bounds.minX, (float) bounds.minY, (float) bounds.minZ)
                .scale(scale);
        if (quarterTurns != 0) {
            matrix.translate(CELL_LOCAL_CENTRE, CELL_LOCAL_CENTRE, CELL_LOCAL_CENTRE)
                    .rotate(Axis.YP.rotationDegrees(QuarterTurns.degrees(quarterTurns)))
                    .translate(-CELL_LOCAL_CENTRE, -CELL_LOCAL_CENTRE, -CELL_LOCAL_CENTRE);
        }
        return matrix;
    }

    /**
     * The cube faces the camera can see, as a bit per {@link Direction} ordinal of the cell-space
     * face. A face is visible exactly when the camera lies on its outward side; the faces that fail
     * are behind the opaque background cube, so skipping them removes nothing that could be seen.
     */
    private static int visibleFaces(Cell cell, float cameraX, float cameraY, float cameraZ) {
        float dx = cell.centreX() - cameraX;
        float dy = cell.centreY() - cameraY;
        float dz = cell.centreZ() - cameraZ;
        Vector3f[] normals = FACE_NORMALS[cell.quarterTurns()];
        int mask = 0;
        for (int face = 0; face < normals.length; face++) {
            Vector3f n = normals[face];
            if (dx * n.x() + dy * n.y() + dz * n.z() < 0.0f) {
                mask |= 1 << face;
            }
        }
        return mask;
    }

    /** Submits prepared cells without consulting the level or inventories. */
    public static void submitGridItems(GridRenderState state, PoseStack pose, SubmitNodeCollector collector) {
        Frame frame = state.frame;
        GridCache cache = frame.cache();
        if (cache == null) {
            return;
        }
        if (hasVisibleFlatCell(frame)) {
            collector.submitCustomGeometry(pose, RenderTypes.solidMovingBlock(),
                    (snapshot, vc) -> emitBackgrounds(frame, snapshot.pose(), vc));
            for (RenderType type : cache.plateTypes) {
                collector.submitCustomGeometry(pose, type,
                        (snapshot, vc) -> emitPlates(frame, type, snapshot.pose(), vc));
            }
        }
        for (Cell cell : cache.cells) {
            Entry entry = cache.entries[cell.entry()];
            if (entry.profile().mode() == RenderMode.TWO_D) {
                continue;
            }
            pose.pushPose();
            try {
                placeCell(pose, cell, cache.scale);
                submitModel(entry, pose, collector, frame.light());
            } finally {
                pose.popPose();
            }
        }
    }

    private static boolean hasVisibleFlatCell(Frame frame) {
        for (int mask : frame.faces()) {
            if (mask != 0) {
                return true;
            }
        }
        return false;
    }

    private static void placeCell(PoseStack pose, Cell cell, float scale) {
        pose.translate(cell.bounds().minX, cell.bounds().minY, cell.bounds().minZ);
        pose.scale(scale, scale, scale);
        if (cell.quarterTurns() != 0) {
            pose.translate(CELL_LOCAL_CENTRE, CELL_LOCAL_CENTRE, CELL_LOCAL_CENTRE);
            pose.mulPose(Axis.YP.rotationDegrees(QuarterTurns.degrees(cell.quarterTurns())));
            pose.translate(-CELL_LOCAL_CENTRE, -CELL_LOCAL_CENTRE, -CELL_LOCAL_CENTRE);
        }
    }

    /** Hands a {@code 3d}, {@code gui}, or {@code block} cell to Minecraft's own renderers. */
    private static void submitModel(Entry entry, PoseStack pose, SubmitNodeCollector collector, int light) {
        RenderProfile profile = entry.profile();
        float scale = profile.scale();
        RenderOffset offset = profile.offset();
        pose.scale(scale, scale, scale);
        pose.translate(CELL_LOCAL_CENTRE / scale, CELL_LOCAL_CENTRE / scale, CELL_LOCAL_CENTRE / scale);
        pose.translate(offset.x() / scale, offset.y() / scale, offset.z() / scale);
        if (entry.block() != null) {
            pose.mulPose(Axis.YP.rotationDegrees(180));
            pose.translate(-0.5, -0.5, -0.5);
            entry.block().submit(pose, collector, light, OverlayTexture.NO_OVERLAY, 0);
        } else if (entry.item() != null) {
            if (profile.mode() == RenderMode.GUI) {
                applyGuiCounterRotation(pose);
            }
            entry.item().submit(pose, collector, light, OverlayTexture.NO_OVERLAY, 0);
        }
    }

    private static final float GUI_Y_COUNTER_ROTATION_DEGREES = -45.0f;
    private static final float GUI_X_COUNTER_ROTATION_DEGREES = -30.0f;

    /** Applies the counter-rotation shared by GUI submission and automatic GUI measurement. */
    public static void applyGuiCounterRotation(PoseStack pose) {
        pose.mulPose(Axis.YP.rotationDegrees(GUI_Y_COUNTER_ROTATION_DEGREES));
        pose.mulPose(Axis.XP.rotationDegrees(GUI_X_COUNTER_ROTATION_DEGREES));
    }

    /** The stack's {@code 2d} art, captured when no retained capture exists for its identity. */
    private static FlatArt flatArt(ItemStack stack, CaptureKey key, Level level) {
        Item item = stack.getItem();
        if (!FLAT_CAPTURE_FAILURES.contains(item)) {
            try {
                FlatArt art = FLAT_ART.get(key);
                if (art == null) {
                    art = new FlatArt(plateBatches(ItemCapture.captureQuads(stack, ItemDisplayContext.FIXED,
                            level, new PoseStack()).quads()), sprite(STACK_CUBE_TEXTURE));
                    FLAT_ART.put(key, art);
                }
                return art;
            } catch (RuntimeException e) {
                FLAT_CAPTURE_FAILURES.add(item);
                SomeStacksCommon.LOGGER.warn("Item model threw while capturing 2d art for {}; drawing the error cube instead",
                        BuiltInRegistries.ITEM.getKey(item), e);
            }
        }
        if (errorArt == null) {
            errorArt = new FlatArt(List.of(), sprite(STACK_CUBE_ERROR_TEXTURE));
        }
        return errorArt;
    }

    private static TextureAtlasSprite sprite(Identifier texture) {
        return Minecraft.getInstance().getAtlasManager().getAtlasOrThrow(AtlasIds.BLOCKS).getSprite(texture);
    }

    /** Clears model-dependent art and failure state and sprite/profile caches on resource reload. */
    public static void onResourceReload() {
        FLAT_CAPTURE_FAILURES.clear();
        FLAT_ART.clear();
        errorArt = null;
        BarStackBER.onResourceReload();
        ItemRenderOverrides.clearResolvedProfiles();
    }

    /**
     * The transform from a baked quad's own coordinates onto one face of the background cube: turn
     * the +Z face onto the target face, sit just outside it keeping a sliver of the quad's own depth,
     * then shrink the art about the face center to leave a border and apply the profile's offset.
     */
    private static Matrix4f artMatrix(Direction face, float scale, RenderOffset offset) {
        Matrix4f art = new Matrix4f();
        Quaternionf rotation = FACE_ROTATIONS[face.ordinal()];
        if (rotation != null) {
            art.translate(0.5f, 0.5f, 0.5f).rotate(rotation).translate(-0.5f, -0.5f, -0.5f);
        }
        return art.translate(0.0f, 0.0f, 1.0f + FACE_OFFSET)
                .scale(1.0f, 1.0f, FACE_DEPTH)
                .translate(0.5f + offset.x(), 0.5f + offset.y(), 0.0f)
                .scale(ART_INSET * scale, ART_INSET * scale, 1.0f)
                .translate(-0.5f, -0.5f, 0.0f);
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
            PlateBatch batch = batches.computeIfAbsent(tinted.quad().materialInfo().itemRenderType(),
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

    /** Draws the visible faces of every {@code 2d} cell's background cube. */
    private static void emitBackgrounds(Frame frame, Matrix4fc blockPose, VertexConsumer vc) {
        Cell[] cells = frame.cache().cells;
        Matrix4f cube = new Matrix4f();
        for (int c = 0; c < cells.length; c++) {
            int faces = frame.faces()[c];
            if (faces == 0) {
                continue;
            }
            Cell cell = cells[c];
            TextureAtlasSprite sprite = frame.cache().entries[cell.entry()].art().background();
            cube.set(blockPose).mul(cell.flatCube());
            for (int face = 0; face < DIRECTIONS.length; face++) {
                if ((faces & (1 << face)) != 0) {
                    emitBackgroundFace(cube, vc, sprite, frame.light(), face);
                }
            }
        }
    }

    /** Lays the plates of one render type onto each visible face of every {@code 2d} cell. */
    private static void emitPlates(Frame frame, RenderType type, Matrix4fc blockPose, VertexConsumer vc) {
        GridCache cache = frame.cache();
        Matrix4f cube = new Matrix4f();
        Matrix4f art = new Matrix4f();
        for (int c = 0; c < cache.cells.length; c++) {
            int faces = frame.faces()[c];
            if (faces == 0) {
                continue;
            }
            Cell cell = cache.cells[c];
            PlateBatch batch = batchOf(cache.entries[cell.entry()].art(), type);
            if (batch == null) {
                continue;
            }
            Matrix4f[] faceArt = cache.entries[cell.entry()].faceArt();
            cube.set(blockPose).mul(cell.flatCube());
            for (int face = 0; face < DIRECTIONS.length; face++) {
                if ((faces & (1 << face)) != 0) {
                    art.set(cube).mul(faceArt[face]);
                    emitBatch(vc, batch, art, frame.light());
                }
            }
        }
    }

    @Nullable
    private static PlateBatch batchOf(FlatArt art, RenderType type) {
        for (PlateBatch batch : art.plates()) {
            if (batch.renderType() == type) {
                return batch;
            }
        }
        return null;
    }

    private static void emitBatch(VertexConsumer vc, PlateBatch batch, Matrix4f art, int light) {
        for (int q = 0; q < batch.quads().size(); q++) {
            BakedQuad quad = batch.quads().get(q);
            int color = batch.colors().get(q);
            int r = ARGB.red(color);
            int g = ARGB.green(color);
            int b = ARGB.blue(color);
            for (int i = 0; i < BakedQuad.VERTEX_COUNT; i++) {
                Vector3fc position = quad.position(i);
                long uv = quad.packedUV(i);
                vertex(vc, art, position.x(), position.y(), position.z(),
                        UVPair.unpackU(uv), UVPair.unpackV(uv), r, g, b, light);
            }
        }
    }

    private static void emitBackgroundFace(Matrix4f cube, VertexConsumer vc, TextureAtlasSprite sp, int light,
                                           int face) {
        float[] c = FACE_CORNERS[face];
        float u0 = sp.getU0(), v0 = sp.getV0(), u1 = sp.getU1(), v1 = sp.getV1();

        vertex(vc, cube, c[0], c[1], c[2], u0, v1, 0xFF, 0xFF, 0xFF, light);
        vertex(vc, cube, c[3], c[4], c[5], u1, v1, 0xFF, 0xFF, 0xFF, light);
        vertex(vc, cube, c[6], c[7], c[8], u1, v0, 0xFF, 0xFF, 0xFF, light);
        vertex(vc, cube, c[9], c[10], c[11], u0, v0, 0xFF, 0xFF, 0xFF, light);
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
