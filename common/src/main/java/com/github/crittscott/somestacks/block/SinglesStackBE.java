package com.github.crittscott.somestacks.block;

import com.github.crittscott.somestacks.CommonRegistry;
import com.github.crittscott.somestacks.server.WorldEdits;
import com.github.crittscott.somestacks.util.ItemOps;
import com.github.crittscott.somestacks.util.SinglesCubeIdx;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import javax.annotation.Nullable;
import java.util.Objects;

/**
 * One Singles Stack: 64 single items drawn as a rotatable 4 x 4 x 4 grid.
 *
 * <p>A slot index is a cell, not a place in a bag, and every item must sit on one directly below it
 * or on the seam with the Singles Stack beneath. A vertical run of these is a {@link SinglesColumn},
 * which is what automation addresses; this class owns one block's cells, its shape, its layout
 * rotation, and a rotation for each rendered item.
 *
 * <p>Capability exposure is loader-specific and lives outside this class; {@link #getItems()} is
 * what a loader-specific capability view adapts.
 */
public class SinglesStackBE extends StackBlockEntity {
    /** Cells in one block. The column's flat range is this times its height. */
    public static final int SLOTS = SinglesCubeIdx.CELLS;

    private static final String TAG_ROTATION = "Rotation";
    private static final String TAG_CUBE_ROTATIONS = "CubeRotations";

    private VoxelShape cachedShape = null;
    private int rotation = 0;
    private int[] cubeRotations = new int[SLOTS];
    /** The column resolved for this block, good for the tick it was taken on. See {@link #column()}. */
    private SinglesColumn cachedColumn;
    private long cachedColumnTick = Long.MIN_VALUE;

    public SinglesStackBE(BlockPos pos, BlockState state) {
        super(CommonRegistry.SINGLES_STACK_BE.get(), pos, state, SLOTS);
    }

    @Override
    protected boolean isStoredItemValid(ItemStack stack) {
        return isValidSinglesItem(stack);
    }

    @Override
    protected int localSlotLimit() {
        return 1;
    }

    @Override
    protected void onLocalContentsChanged(int slot) {
        cachedShape = null;
    }

    @Override
    protected void markRunDirty() {
        SinglesColumn column = column();
        if (column != null) {
            column.markDirty();
        }
    }

    /**
     * The column this block belongs to, or null on the client and for a block being removed.
     *
     * <p>Resolved once a tick and held, because a machine reading this block's item handler resolves
     * the column once per slot it walks. See {@link StorageStackBE#pile()} for the full reasoning.
     */
    @Nullable
    public SinglesColumn column() {
        if (!(level instanceof ServerLevel serverLevel)) {
            return null;
        }

        long now = serverLevel.getGameTime();
        if (cachedColumn != null && cachedColumnTick == now) {
            return cachedColumn;
        }

        cachedColumn = SinglesColumn.resolve(serverLevel, getBlockPos());
        cachedColumnTick = now;
        return cachedColumn;
    }

    @Override
    public StackRunItemAccess itemRun() {
        return column();
    }

    /** Invalidates the cached column so the next lookup walks the world again. */
    void invalidateColumn() {
        cachedColumn = null;
    }

    /**
     * Whether Singles accepts this item. Bar-valid items are excluded, so widening the ingot tags
     * narrows what Singles takes by the same set.
     */
    public static boolean isValidSinglesItem(ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        }
        if (ItemOps.isItemFromDisabledMod(stack)) {
            return false;
        }
        return !BarStackBE.isValidBarItem(stack);
    }

    public int getRotation() {
        return rotation;
    }

    public void setRotation(int rotation) {
        this.rotation = rotation % 4;
        setChanged();
        cachedShape = null;
        if (!isBatching()) {
            syncToClients();
        }
    }

    /** The rendered rotation of the item at {@code index}, which must be in {@code [0, SLOTS)}. */
    public int getCubeRotation(int index) {
        return cubeRotations[index];
    }

    /** Sets the rendered rotation at {@code index}, which must be in {@code [0, SLOTS)}. */
    public void setCubeRotation(int index, int cubeRot) {
        cubeRotations[index] = cubeRot % 4;
        setChanged();
        if (!isBatching()) {
            syncToClients();
        }
    }

    /** Builds the union of the occupied cells' boxes at the current layout rotation. */
    public VoxelShape computeShape() {
        VoxelShape shape = Shapes.empty();
        int blockRotation = this.rotation;

        for (int i = 0; i < items.getSlots(); i++) {
            ItemStack stack = items.getStackInSlot(i);
            if (!stack.isEmpty()) {
                shape = Shapes.or(shape, SinglesCubeIdx.shapeFor(i, blockRotation));
            }
        }

        return shape;
    }

    /**
     * Returns the occupied-cell shape, rebuilding it only after contents or rotation invalidates
     * the cache. Shape queries occur frequently enough that they must not rebuild it unconditionally.
     */
    public VoxelShape getCachedShape() {
        if (cachedShape == null) {
            cachedShape = computeShape();
        }
        return cachedShape;
    }

    /**
     * Puts one item from the given stack into the named cell, if that cell is empty and supported.
     * Refuses rather than redirecting the item elsewhere, so a caller walking cells from the bottom
     * fills the supported ones in order.
     *
     * @param index the cell to fill, in {@code [0, SLOTS)}
     * @return whether the item moved; the hand shrinks by one only then
     */
    public boolean depositAt(int index, ItemStack fromHand) {
        if (fromHand.isEmpty()) {
            return false;
        }

        if (!items.getStackInSlot(index).isEmpty()) {
            return false;
        }

        if (!SinglesCubeIdx.isGrounded(index, items, rotation, seamBeneath())) {
            return false;
        }

        ItemStack toInsert = fromHand.copy();
        toInsert.setCount(1);
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
     * @param index the cell to empty, in {@code [0, SLOTS)}
     * @return the extracted item, or empty when the cell held nothing
     */
    public ItemStack extractAt(int index) {
        ServerLevel serverLevel = (ServerLevel) Objects.requireNonNull(level);
        int column = SinglesCubeIdx.columnFromIndex(index);
        int y = SinglesCubeIdx.xyzFromIndex(index)[1];

        ItemStack extracted;
        beginBatch();
        try {
            extracted = items.extractItem(index, 1, false);
            if (extracted.isEmpty()) {
                return ItemStack.EMPTY;
            }
            shiftColumnDown(column, y);
        } finally {
            endBatchWithoutPublish();
        }

        drawDownColumn(serverLevel, column);
        return extracted;
    }

    /**
     * The support this block's bottom layer rests on: the top-layer occupancy of the Singles Stack
     * directly below, or null when this block stands on the world instead of on another Singles
     * Stack.
     */
    private boolean[] seamBeneath() {
        if (level != null && level.getBlockEntity(getBlockPos().below()) instanceof SinglesStackBE below) {
            return SinglesCubeIdx.topLayerOccupancy(below.items, below.rotation);
        }
        return null;
    }

    /**
     * Closes the gap one column left behind: every occupied cell above {@code fromY} moves down
     * exactly one layer, carrying its rotation. The shift is positional rather than a compaction, so
     * gaps that capability inserts created are preserved rather than quietly closed, and it always
     * leaves the top cell of the column empty for the block above to hand down into.
     *
     * <p>The cell moved into is always empty — vacated by the extraction that started the pass,
     * emptied by the previous step, or empty already — so the item is written straight into it
     * rather than inserted. Validity gates what a deposit may add rather than what the structure may
     * carry, so an item stored before an {@code ss ingot} edit or a data pack reload narrowed the
     * rule still moves with its column.
     */
    private void shiftColumnDown(int column, int fromY) {
        for (int checkY = fromY + 1; checkY < SinglesCubeIdx.LAYERS; checkY++) {
            int sourceIndex = SinglesCubeIdx.indexFromColumn(column, checkY);

            if (!items.getStackInSlot(sourceIndex).isEmpty()) {
                int targetIndex = SinglesCubeIdx.indexFromColumn(column, checkY - 1);

                items.setStackInSlot(targetIndex, items.extractItem(sourceIndex, 1, false));

                cubeRotations[targetIndex] = cubeRotations[sourceIndex];
            }
        }
    }

    /**
     * Draws a column down through the Singles Stacks above, so a vertical run behaves as one stack.
     * Each shift vacates the top cell of its column, so exactly one item crosses each block boundary
     * and the receiving cell is always free. The column is matched between blocks through visual
     * coordinates, because two stacked blocks may carry different rotations and the run the player
     * sees as continuous is the visual one. The walk ends at the first block that hands nothing
     * down. The receiving cell is written directly, for the reason {@link #shiftColumnDown} gives.
     */
    private void drawDownColumn(ServerLevel columnLevel, int column) {
        SinglesStackBE be = this;
        int col = column;

        while (true) {
            SinglesStackBE next = null;
            int nextColumn = -1;

            if (columnLevel.getBlockEntity(be.getBlockPos().above()) instanceof SinglesStackBE above) {
                int visualColumn = SinglesCubeIdx.visualColumnFromStorage(col, be.rotation);
                int aboveColumn = SinglesCubeIdx.storageColumnFromVisual(visualColumn, above.rotation);

                int sourceIndex = SinglesCubeIdx.indexFromColumn(aboveColumn, 0);

                if (!above.items.getStackInSlot(sourceIndex).isEmpty()) {
                    int targetIndex = SinglesCubeIdx.indexFromColumn(col, SinglesCubeIdx.TOP_LAYER_Y);

                    be.beginBatch();
                    above.beginBatch();
                    try {
                        be.items.setStackInSlot(targetIndex,
                                above.items.extractItem(sourceIndex, 1, false));
                        // Block rotation turns a cell's position but never the item in it, so the
                        // stored rotation carries an item's facing across a change of frame as is.
                        be.cubeRotations[targetIndex] = above.cubeRotations[sourceIndex];
                        above.shiftColumnDown(aboveColumn, 0);
                    } finally {
                        above.endBatchWithoutPublish();
                        be.endBatchWithoutPublish();
                    }

                    next = above;
                    nextColumn = aboveColumn;
                }
            }

            be.publishColumnEdit(columnLevel);

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
    private void publishColumnEdit(ServerLevel columnLevel) {
        clearEmptyCubeRotations();

        if (isEmpty()
                && !(columnLevel.getBlockEntity(getBlockPos().above()) instanceof SinglesStackBE)
                && WorldEdits.removeChecked(columnLevel, getBlockPos())) {
            removeEmptyBelow(columnLevel, getBlockPos().below());
            return;
        }

        setChanged();
        requestPublish();
    }

    /** Stops at the first block that is not an empty Singles Stack, or that protection keeps. */
    private static void removeEmptyBelow(ServerLevel columnLevel, BlockPos pos) {
        BlockPos current = pos;

        while (columnLevel.getBlockEntity(current) instanceof SinglesStackBE be && be.isEmpty()) {
            if (!WorldEdits.removeChecked(columnLevel, current)) {
                return;
            }
            current = current.below();
        }
    }

    /**
     * An empty cell carries no orientation, so the next item deposited into it cannot inherit
     * the previous occupant's rotation.
     */
    private void clearEmptyCubeRotations() {
        for (int i = 0; i < cubeRotations.length; i++) {
            if (items.getStackInSlot(i).isEmpty()) {
                cubeRotations[i] = 0;
            }
        }
    }

    @Override
    protected void loadStackData(CompoundTag tag, HolderLookup.Provider registries) {
        if (tag.contains(TAG_ROTATION)) {
            rotation = tag.getInt(TAG_ROTATION);
        }
        if (tag.contains(TAG_CUBE_ROTATIONS)) {
            int[] loaded = tag.getIntArray(TAG_CUBE_ROTATIONS);
            if (loaded.length == SLOTS) {
                cubeRotations = loaded.clone();
            }
        }
        cachedShape = null;
    }

    @Override
    protected void saveStackData(CompoundTag tag, HolderLookup.Provider registries) {
        tag.putInt(TAG_ROTATION, rotation);
        // IntArrayTag holds the array it is given, and an integrated server hands its update
        // packets to the client unserialized: both sides must get their own copy, or the client
        // renders the server's in-progress rotations against its own not-yet-updated items.
        tag.putIntArray(TAG_CUBE_ROTATIONS, cubeRotations.clone());
    }

}
