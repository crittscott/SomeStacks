package com.github.crittscott.somestacks.block;

import com.github.crittscott.somestacks.CommonRegistry;
import com.github.crittscott.somestacks.server.WorldEdits;
import com.github.crittscott.somestacks.util.BlockType;
import com.github.crittscott.somestacks.util.CubeGrid;
import com.github.crittscott.somestacks.util.ItemOps;
import com.github.crittscott.somestacks.util.SinglesCubeIdx;
import com.github.crittscott.somestacks.util.ViewRay;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import javax.annotation.Nullable;

/**
 * One Singles Stack: 64 single items drawn as a rotatable 4 x 4 x 4 grid.
 *
 * <p>A slot index is a cell, not a place in a bag, and every item must sit on one directly below it
 * or on the seam with the Singles Stack beneath. A vertical run of these is a {@link
 * SinglesColumn}, which is what automation addresses; this class owns one block's cells, its shape,
 * its layout rotation, and a rotation for each rendered item.
 *
 * <p>Local slots are available through {@link #getItems()}; loader automation adapters expose the
 * whole run through {@link #itemRun()}.
 */
public class SinglesStackBE extends StackBlockEntity {
    /** Cells in one block. The column's flat range is this times its height. */
    public static final int SLOTS = SinglesCubeIdx.CELLS;

    private static final String TAG_CUBE_ROTATIONS = "CubeRotations";

    private VoxelShape cachedShape = null;
    private int cachedShapeRotation = -1;
    private int[] cubeRotations = new int[SLOTS];

    public SinglesStackBE(BlockPos pos, BlockState state) {
        super(CommonRegistry.SINGLES_STACK_BE.get(), pos, state, SLOTS);
    }

    /**
     * Returns the server column containing this block, or null on the client or when removed.
     * Delegates to {@link StackBlockEntity#itemRun()}, whose per-tick cache is invalidated by
     * structural edits.
     */
    @Nullable
    public SinglesColumn column() {
        return (SinglesColumn) itemRun();
    }

    /** The rendered rotation of the item in cell {@code index}, in quarter turns. */
    public int getCubeRotation(int index) {
        return cubeRotations[index];
    }

    /**
     * Sets the rendered rotation of the item in cell {@code index}, which must name a cell, to
     * {@code cubeRot} quarter turns taken modulo a full turn, and marks the block changed. Outside
     * a batch it syncs clients at once; inside one it leaves publication to the batch.
     */
    public void setCubeRotation(int index, int cubeRot) {
        cubeRotations[index] = Math.floorMod(cubeRot, CubeGrid.ROTATIONS);
        setChanged();
        if (!isBatching()) {
            syncToClients();
        }
    }

    /** Builds the union of the occupied cells' boxes at the current layout rotation. */
    public VoxelShape computeShape() {
        VoxelShape shape = Shapes.empty();
        int blockRotation = getRotation();

        for (int i = 0; i < items.getSlots(); i++) {
            ItemStack stack = items.getStackInSlot(i);
            if (!stack.isEmpty()) {
                shape =
                        Shapes.joinUnoptimized(
                                shape,
                                SinglesCubeIdx.GRID.localShape(i, blockRotation),
                                BooleanOp.OR);
            }
        }

        return shape.optimize();
    }

    /**
     * Returns the occupied-cell shape, rebuilding it only after contents or rotation invalidates
     * the cache. Shape queries occur frequently enough that they must not rebuild it
     * unconditionally.
     */
    public VoxelShape getCachedShape() {
        int blockRotation = getRotation();
        if (cachedShape == null || cachedShapeRotation != blockRotation) {
            cachedShape = computeShape();
            cachedShapeRotation = blockRotation;
        }
        return cachedShape;
    }

    /** Whether cell {@code index} is empty and resting on an item or the seam beneath it. */
    public boolean canDepositAt(int index) {
        return items.getStackInSlot(index).isEmpty()
                && SinglesCubeIdx.isGrounded(index, items, getRotation(), seamBeneath());
    }

    @Override
    public boolean acceptsDepositAlong(ViewRay ray) {
        int index = depositTarget(ray);
        return index >= 0 && canDepositAt(index);
    }

    /** Puts one item into the cell the ray targets; see {@link #depositAt}. */
    @Override
    public int depositFromHand(ViewRay ray, ItemStack fromHand, ServerPlayer player) {
        int index = depositTarget(ray);
        return index >= 0 && depositAt(index, fromHand) ? 1 : 0;
    }

    /** Takes the one item only when the hand can hold it; see {@link #extractAt}. */
    @Override
    public ItemStack extractIntoHand(int index, ItemStack hand, ServerPlayer player) {
        return ItemOps.canTakeIntoHand(hand, items.getStackInSlot(index))
                ? extractAt(index, player)
                : ItemStack.EMPTY;
    }

    /**
     * Puts one item from the given stack into the named cell, if that cell is empty and supported
     * and the item is admitted. Refuses rather than redirecting the item elsewhere, so a caller
     * walking cells from the bottom fills the supported ones in order.
     *
     * @return whether the item moved; the hand shrinks by one only then
     */
    public boolean depositAt(int index, ItemStack fromHand) {
        if (fromHand.isEmpty() || !canDepositAt(index)) {
            return false;
        }

        ItemStack toInsert = fromHand.copyWithCount(1);
        ItemStack remainder = items.insertItem(index, toInsert, false);

        if (remainder.isEmpty()) {
            fromHand.shrink(1);
            return true;
        }

        return false;
    }

    /**
     * Takes the item out of the named cell and settles the visual column above it downward, drawing
     * items across block seams as needed so nothing is left unsupported.
     *
     * @return the extracted item, or empty when the cell held nothing
     */
    public ItemStack extractAt(int index) {
        return extractAt(index, null);
    }

    /**
     * Player-facing extraction variant that preserves the actor through structural cleanup.
     *
     * @param actor the player responsible for any block the extraction empties away, or null for
     *     automation
     */
    public ItemStack extractAt(int index, @Nullable ServerPlayer actor) {
        int column = SinglesCubeIdx.columnFromIndex(index);
        int y = SinglesCubeIdx.GRID.xyzFromIndex(index)[1];

        ItemStack extracted;
        beginBatch();
        try {
            extracted = items.extractItem(index, 1, false);
            if (extracted.isEmpty()) {
                return ItemStack.EMPTY;
            }
            shiftColumnDown(column, y);
        } finally {
            endBatch();
        }

        drawDownColumn((ServerLevel) level, column, actor);
        return extracted;
    }

    /** This block's own seam; see {@link #seamBeneath(Level, BlockPos)}. */
    @Nullable
    private boolean[] seamBeneath() {
        return seamBeneath(level, getBlockPos());
    }

    /**
     * The support the bottom layer of a Singles Stack at {@code pos} rests on: the top-layer
     * occupancy of the Singles Stack directly below, or null when it stands on the world instead.
     */
    @Nullable
    static boolean[] seamBeneath(Level level, BlockPos pos) {
        if (level.getBlockEntity(pos.below()) instanceof SinglesStackBE below) {
            return SinglesCubeIdx.topLayerOccupancy(below.items, below.getRotation());
        }
        return null;
    }

    /**
     * Moves occupied cells above {@code fromY} down exactly one layer in this local column,
     * carrying item rotation and preserving gaps. The caller supplies a gap at {@code fromY};
     * stored items remain movable even when current admission rules would refuse them.
     */
    private void shiftColumnDown(int column, int fromY) {
        for (int checkY = fromY + 1; checkY < SinglesCubeIdx.LAYERS; checkY++) {
            int sourceIndex = SinglesCubeIdx.indexFromColumn(column, checkY);

            if (!items.getStackInSlot(sourceIndex).isEmpty()) {
                int targetIndex = SinglesCubeIdx.indexFromColumn(column, checkY - 1);

                // The destination is vacant; direct assignment preserves stored items regardless of
                // admission.
                items.setStackInSlot(targetIndex, items.extractItem(sourceIndex, 1, false));

                cubeRotations[targetIndex] = cubeRotations[sourceIndex];
            }
        }
    }

    /**
     * Draws items down across contiguous Singles blocks along the same visual column, matching
     * block rotations and carrying item rotations. Requires a vacant top cell in the receiving
     * column; stops when the next block cannot hand an item down. Does not recheck admission.
     */
    private void drawDownColumn(ServerLevel columnLevel, int column, @Nullable ServerPlayer actor) {
        SinglesStackBE be = this;
        int col = column;

        while (true) {
            SinglesStackBE next = null;
            int nextColumn = -1;

            if (columnLevel.getBlockEntity(be.getBlockPos().above())
                    instanceof SinglesStackBE above) {
                int visualColumn = SinglesCubeIdx.visualColumnFromStorage(col, be.getRotation());
                int aboveColumn =
                        SinglesCubeIdx.storageColumnFromVisual(visualColumn, above.getRotation());

                int sourceIndex = SinglesCubeIdx.indexFromColumn(aboveColumn, 0);

                if (!above.items.getStackInSlot(sourceIndex).isEmpty()) {
                    int targetIndex =
                            SinglesCubeIdx.indexFromColumn(col, SinglesCubeIdx.TOP_LAYER_Y);

                    be.beginBatch();
                    above.beginBatch();
                    try {
                        // Gravity transfers stored contents without applying deposit admission.
                        be.items.setStackInSlot(
                                targetIndex, above.items.extractItem(sourceIndex, 1, false));
                        // Block rotation turns a cell's position but never the item in it, so the
                        // stored rotation carries an item's facing across a change of frame as is.
                        be.cubeRotations[targetIndex] = above.cubeRotations[sourceIndex];
                        above.shiftColumnDown(aboveColumn, 0);
                    } finally {
                        above.endBatch();
                        be.endBatch();
                    }

                    next = above;
                    nextColumn = aboveColumn;
                }
            }

            be.publishColumnEdit(columnLevel, actor);

            if (next == null) {
                return;
            }

            be = next;
            col = nextColumn;
        }
    }

    /**
     * Publishes one block's settled contents. An emptied block removes itself, except while another
     * Singles Stack sits directly above it: severing a column there would strand the run above with
     * nothing to fall onto. Removing a block therefore clears any empty blocks it was covering.
     *
     * <p>A block protection refuses to remove stays, and publishes as the empty block it now is.
     */
    private void publishColumnEdit(ServerLevel columnLevel, @Nullable ServerPlayer actor) {
        clearEmptyCubeRotations();

        if (isEmpty()
                && !(columnLevel.getBlockEntity(getBlockPos().above()) instanceof SinglesStackBE)
                && WorldEdits.removeChecked(actor, columnLevel, getBlockPos())) {
            removeEmptyBelow(columnLevel, getBlockPos().below(), actor);
            return;
        }

        setChanged();
        schedulePublish();
    }

    /** Stops at the first block that is not an empty Singles Stack, or that protection keeps. */
    private static void removeEmptyBelow(
            ServerLevel columnLevel, BlockPos pos, @Nullable ServerPlayer actor) {
        BlockPos current = pos;

        while (columnLevel.getBlockEntity(current) instanceof SinglesStackBE be && be.isEmpty()) {
            if (!WorldEdits.removeChecked(actor, columnLevel, current)) {
                return;
            }
            current = current.below();
        }
    }

    /**
     * An empty cell carries no orientation, so the next item deposited into it cannot inherit the
     * previous occupant's rotation.
     */
    private void clearEmptyCubeRotations() {
        for (int i = 0; i < cubeRotations.length; i++) {
            if (items.getStackInSlot(i).isEmpty()) {
                cubeRotations[i] = 0;
            }
        }
    }

    @Override
    protected boolean isValidClientStackData(CompoundTag tag) {
        if (!tag.contains(TAG_CUBE_ROTATIONS, Tag.TAG_INT_ARRAY)) return false;
        int[] rotations = tag.getIntArray(TAG_CUBE_ROTATIONS);
        if (rotations.length != SLOTS) return false;
        for (int rotation : rotations) {
            if (rotation < 0 || rotation >= CubeGrid.ROTATIONS) return false;
        }
        return true;
    }

    @Override
    protected void loadStackData(CompoundTag tag) {
        if (tag.contains(TAG_CUBE_ROTATIONS)) {
            int[] loaded = tag.getIntArray(TAG_CUBE_ROTATIONS);
            if (loaded.length == SLOTS) {
                cubeRotations = loaded.clone();
                for (int i = 0; i < cubeRotations.length; i++) {
                    cubeRotations[i] = Math.floorMod(cubeRotations[i], CubeGrid.ROTATIONS);
                }
            }
        }
        cachedShape = null;
    }

    @Override
    protected void saveStackData(CompoundTag tag) {
        // IntArrayTag holds the array it is given, and an integrated server hands its update
        // packets to the client unserialized: both sides must get their own copy, or the client
        // renders the server's in-progress rotations against its own not-yet-updated items.
        tag.putIntArray(TAG_CUBE_ROTATIONS, cubeRotations.clone());
    }

    @Override
    public boolean admits(ItemStack stack) {
        return BlockType.SINGLES_STACK.admits(stack, null);
    }

    @Override
    protected AABB cellBox(int index) {
        return SinglesCubeIdx.GRID.localBox(index, getRotation());
    }

    @Override
    protected StackRun<?> resolveRun(ServerLevel serverLevel) {
        return SinglesColumn.resolve(serverLevel, getBlockPos());
    }

    @Override
    public int slotLimit() {
        return 1;
    }

    @Override
    protected void onLocalContentsChanged(int slot) {
        cachedShape = null;
    }
}
