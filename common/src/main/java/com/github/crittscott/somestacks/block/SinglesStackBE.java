package com.github.crittscott.somestacks.block;

import com.github.crittscott.somestacks.CommonRegistry;
import com.github.crittscott.somestacks.server.WorldEdits;
import com.github.crittscott.somestacks.util.ItemOps;
import com.github.crittscott.somestacks.util.SinglesCubeIdx;
import com.github.crittscott.somestacks.util.StackItemStorage;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import javax.annotation.Nonnull;
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
public class SinglesStackBE extends BlockEntity {
    /** Cells in one block. The column's flat range is this times its height. */
    public static final int SLOTS = SinglesCubeIdx.CELLS;

    private static final String TAG_ITEMS = "Items";
    private static final String TAG_ROTATION = "Rotation";
    private static final String TAG_CUBE_ROTATIONS = "CubeRotations";

    private VoxelShape cachedShape = null;
    private int rotation = 0;
    private int[] cubeRotations = new int[SLOTS];
    private boolean suppressSync = false;
    private boolean batchTouched = false;

    /**
     * Set when a content edit still requires client synchronization and a light update. The
     * column's scheduled publication pass clears it; see {@link #schedulePublish()}.
     */
    private boolean publishPending = false;

    /**
     * The comparator output last published for the column this block is the bottom of, or -1 before
     * the first publication. Only the bottom block's copy is consulted, and it is runtime state
     * rather than saved NBT: a freshly loaded column has published nothing, so its first change
     * should notify.
     */
    private int publishedSignal = -1;

    /** The column resolved for this block, good for the tick it was taken on. See {@link #column()}. */
    private SinglesColumn cachedColumn;
    private long cachedColumnTick = Long.MIN_VALUE;

    private final StackItemStorage items = new StackItemStorage(SLOTS) {
        @Override
        protected void onContentsChanged(int slot) {
            setChanged();
            cachedShape = null;
            if (suppressSync) {
                batchTouched = true;
            } else {
                schedulePublish();
            }
        }

        @Override
        public int getSlotLimit(int slot) {
            return 1;
        }

        @Override
        public boolean isItemValid(int slot, @Nonnull ItemStack stack) {
            return isValidSinglesItem(stack);
        }
    };

    public SinglesStackBE(BlockPos pos, BlockState state) {
        super(CommonRegistry.SINGLES_STACK_BE.get(), pos, state);
    }

    /**
     * The column this block belongs to, or null on the client and for a block being removed.
     *
     * <p>Resolved once a tick and held, because a machine reading this block's item handler resolves
     * the column once per slot it walks. See {@link StorageStackBE#pile()} for the full reasoning.
     */
    @Nullable
    public SinglesColumn column() {
        if (level == null || level.isClientSide) {
            return null;
        }

        long now = level.getGameTime();
        if (cachedColumn != null && cachedColumnTick == now) {
            return cachedColumn;
        }

        cachedColumn = SinglesColumn.resolve(level, getBlockPos());
        cachedColumnTick = now;
        return cachedColumn;
    }

    /** Invalidates the cached column so the next lookup walks the world again. */
    void invalidateColumn() {
        cachedColumn = null;
    }

    /**
     * Records the comparator output the column is about to publish.
     *
     * @return whether the signal changed and comparator neighbors must be notified
     */
    boolean exchangePublishedSignal(int signal) {
        if (publishedSignal == signal) {
            return false;
        }
        publishedSignal = signal;
        return true;
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
        if (!suppressSync) {
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
        if (!suppressSync) {
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
        suppressSync = true;
        try {
            extracted = items.extractItem(index, 1, false);
            if (extracted.isEmpty()) {
                return ItemStack.EMPTY;
            }
            shiftColumnDown(column, y);
        } finally {
            suppressSync = false;
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

                    be.suppressSync = true;
                    above.suppressSync = true;
                    try {
                        be.items.setStackInSlot(targetIndex,
                                above.items.extractItem(sourceIndex, 1, false));
                        // Block rotation turns a cell's position but never the item in it, so the
                        // stored rotation carries an item's facing across a change of frame as is.
                        be.cubeRotations[targetIndex] = above.cubeRotations[sourceIndex];
                        above.shiftColumnDown(aboveColumn, 0);
                    } finally {
                        above.suppressSync = false;
                        be.suppressSync = false;
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

    public boolean isEmpty() {
        return ItemOps.isHandlerEmpty(items);
    }

    /** Synchronizes this block's contents and presentation state to tracking clients. */
    public void syncToClients() {
        if (level != null) {
            BlockState state = getBlockState();
            level.sendBlockUpdated(getBlockPos(), state, state, Block.UPDATE_ALL);
        }
    }

    /**
     * Opens a batch of edits that should publish once. Per-slot synchronization is suppressed, and
     * {@link #endBatch()} schedules publication only if this block changed. Gravity publishes
     * through its own path, so a new batch clears any prior touch state.
     */
    void beginBatch() {
        suppressSync = true;
        batchTouched = false;
    }

    void endBatch() {
        suppressSync = false;
        if (batchTouched) {
            batchTouched = false;
            schedulePublish();
        }
    }

    /**
     * Marks this block for publication and schedules a pass on the column's bottom block.
     *
     * <p>Publishing costs an update packet, a comparator walk of the whole column, and a light
     * recompute. A cell holds one item, so a caller moving a stack calls the handler once per item,
     * and one removal draws an item down out of every block above it; deferring to a block tick on
     * the column's bottom keeps either from paying that at each step.
     */
    private void schedulePublish() {
        if (level == null || level.isClientSide) {
            return;
        }
        publishPending = true;

        SinglesColumn column = column();
        if (column != null) {
            column.markDirty();
        }
    }

    /**
     * Publishes this block's deferred contents and emitted light level. Comparator output belongs
     * to the column and is published once for the whole run by
     * {@link SinglesColumn#publishPending()}.
     *
     * @return whether anything was owed
     */
    boolean publishIfPending() {
        Level columnLevel = level;
        if (columnLevel == null || columnLevel.isClientSide || !publishPending) {
            return false;
        }
        publishPending = false;
        syncToClients();

        int newLight = ItemOps.calculateLightLevelFromItems(items);
        BlockState state = getBlockState();
        if (state.getValue(SinglesStackBlock.LIGHT_LEVEL) != newLight) {
            columnLevel.setBlock(getBlockPos(),
                    state.setValue(SinglesStackBlock.LIGHT_LEVEL, newLight), Block.UPDATE_ALL);
        }
        return true;
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.contains(TAG_ITEMS)) {
            items.deserializeNBT(registries, tag.getCompound(TAG_ITEMS));
        }
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
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.put(TAG_ITEMS, items.serializeNBT(registries));
        tag.putInt(TAG_ROTATION, rotation);
        // IntArrayTag holds the array it is given, and an integrated server hands its update
        // packets to the client unserialized: both sides must get their own copy, or the client
        // renders the server's in-progress rotations against its own not-yet-updated items.
        tag.putIntArray(TAG_CUBE_ROTATIONS, cubeRotations.clone());
    }

    /** This block's 64 slots, for callers that already hold the block entity. */
    public StackItemStorage getItems() {
        return items;
    }

    @Override
    public ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        saveAdditional(tag, registries);
        return tag;
    }

}
