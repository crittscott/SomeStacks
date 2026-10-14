package com.github.crittscott.somestacks.block;

import com.github.crittscott.somestacks.CommonRegistry;
import com.github.crittscott.somestacks.server.WorldEdits;
import com.github.crittscott.somestacks.util.ItemOps;
import com.github.crittscott.somestacks.util.SinglesCubeIdx;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import javax.annotation.Nullable;

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

    public SinglesStackBE(BlockPos pos, BlockState state) {
        super(CommonRegistry.SINGLES_STACK_BE.get(), pos, state, SLOTS, SinglesStackBlock.LIGHT_LEVEL);
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

    /** The rendered rotation of one item, in quarter turns. Out-of-range indexes read as unrotated. */
    public int getCubeRotation(int index) {
        if (index < 0 || index >= SLOTS) {
            return 0;
        }
        return cubeRotations[index];
    }

    public void setCubeRotation(int index, int cubeRot) {
        if (index < 0 || index >= SLOTS) {
            return;
        }
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
     * @return whether the item moved; the hand shrinks by one only then
     */
    public boolean depositAt(int index, ItemStack fromHand) {
        if (fromHand.isEmpty()) {
            return false;
        }

        if (index < 0 || index >= SLOTS) {
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
     * @return the extracted item, or empty when the cell held nothing
     */
    public ItemStack extractAt(int index) {
        if (index < 0 || index >= SLOTS) {
            return ItemStack.EMPTY;
        }

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
            endBatch();
        }

        if (level == null || level.isClientSide) {
            clearEmptyCubeRotations();
            setChanged();
            schedulePublish();
            return extracted;
        }

        drawDownColumn(column);
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
     * Moves occupied cells above {@code fromY} down exactly one layer in this local column, carrying
     * item rotation and preserving gaps. The caller supplies a gap at {@code fromY}; stored items
     * remain movable even when current admission rules would refuse them.
     */
    private void shiftColumnDown(int column, int fromY) {
        for (int checkY = fromY + 1; checkY < SinglesCubeIdx.LAYERS; checkY++) {
            int sourceIndex = SinglesCubeIdx.indexFromColumn(column, checkY);

            if (!items.getStackInSlot(sourceIndex).isEmpty()) {
                int targetIndex = SinglesCubeIdx.indexFromColumn(column, checkY - 1);

                // The destination is vacant; direct assignment preserves stored items regardless of admission.
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
    private void drawDownColumn(int column) {
        Level columnLevel = level;
        if (columnLevel == null) {
            return;
        }

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
                        // Gravity transfers stored contents without applying deposit admission.
                        be.items.setStackInSlot(targetIndex,
                                above.items.extractItem(sourceIndex, 1, false));
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
    private void publishColumnEdit(Level columnLevel) {
        clearEmptyCubeRotations();

        if (isEmpty()
                && !(columnLevel.getBlockEntity(getBlockPos().above()) instanceof SinglesStackBE)
                && columnLevel instanceof ServerLevel serverLevel
                && WorldEdits.removeChecked(serverLevel, getBlockPos())) {
            removeEmptyBelow(serverLevel, getBlockPos().below());
            return;
        }

        setChanged();
        schedulePublish();
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
    protected void loadStackData(CompoundTag tag) {
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
    protected void saveStackData(CompoundTag tag) {
        tag.putInt(TAG_ROTATION, rotation);
        // IntArrayTag holds the array it is given, and an integrated server hands its update
        // packets to the client unserialized: both sides must get their own copy, or the client
        // renders the server's in-progress rotations against its own not-yet-updated items.
        tag.putIntArray(TAG_CUBE_ROTATIONS, cubeRotations.clone());
    }

    @Override
    protected boolean isStoredItemValid(ItemStack stack) {
        return isValidSinglesItem(stack);
    }

    @Override
    protected StackRunItemAccess resolveRun(ServerLevel serverLevel) {
        return SinglesColumn.resolve(serverLevel, getBlockPos());
    }

    @Override
    protected int localSlotLimit() {
        return 1;
    }

    @Override
    protected void onLocalContentsChanged(int slot) {
        cachedShape = null;
    }
}
