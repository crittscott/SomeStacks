package com.github.crittscott.somestacks.block;

import com.github.crittscott.somestacks.ModRegistry;
import com.github.crittscott.somestacks.util.ItemOps;
import com.github.crittscott.somestacks.util.SinglesCubeIdx;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemStackHandler;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public class SinglesStackBE extends BlockEntity {
    /** Cells in one block. The column's flat range is this times its height. */
    public static final int SLOTS = 64;

    private VoxelShape cachedShape = null;
    private int rotation = 0;
    private int[] cubeRotations = new int[SLOTS];
    private boolean suppressSync = false;
    private boolean batchTouched = false;

    /**
     * Set when a content edit still owes clients this block's contents and the block its light
     * level. The column's scheduled publication pass clears it; see {@link #schedulePublish()}.
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

    private final ItemStackHandler items = new ItemStackHandler(SLOTS) {
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
    private LazyOptional<IItemHandler> itemsCap = LazyOptional.of(() -> new SinglesColumnHandler(this));

    public SinglesStackBE(BlockPos pos, BlockState state) {
        super(ModRegistry.SINGLES_STACK_BE.get(), pos, state);
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

    /** Drops the held column, so the next caller walks the world again. */
    void invalidateColumn() {
        cachedColumn = null;
    }

    /**
     * Records the comparator output the column is about to publish.
     *
     * @return whether it differs from the last one, and so whether the column needs to tell its
     *         neighbours to read again
     */
    boolean exchangePublishedSignal(int signal) {
        if (publishedSignal == signal) {
            return false;
        }
        publishedSignal = signal;
        return true;
    }

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

    public VoxelShape getCachedShape() {
        if (cachedShape == null) {
            cachedShape = computeShape();
        }
        return cachedShape;
    }

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
     * <p>The cell moved into is always empty — it was either vacated by the extraction that started
     * the pass, emptied by the previous step, or skipped for being empty already — so the item is
     * written straight into it. Going through {@link ItemStackHandler#insertItem} would put the
     * block's deposit rule in the way of an item the block already holds, and validity gates what a
     * deposit may add rather than what the structure may carry: an item stored before an
     * {@code ss ingot} edit or a data pack reload narrowed the rule still has to move with it.
     */
    private void shiftColumnDown(int column, int fromY) {
        for (int checkY = fromY + 1; checkY < 4; checkY++) {
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
     * sees as continuous is the visual one. The walk ends at the first block that hands nothing down.
     *
     * <p>The receiving cell is written directly for the reason {@link #shiftColumnDown} gives: the
     * item is already stored in the column, so the deposit rule has no say in whether it may move.
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
                    int targetIndex = SinglesCubeIdx.indexFromColumn(col, 3);

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
     */
    private void publishColumnEdit(Level columnLevel) {
        clearEmptyCubeRotations();

        if (isEmpty() && !(columnLevel.getBlockEntity(getBlockPos().above()) instanceof SinglesStackBE)) {
            columnLevel.setBlock(getBlockPos(), Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
            removeEmptyBelow(columnLevel, getBlockPos().below());
            return;
        }

        setChanged();
        schedulePublish();
    }

    private static void removeEmptyBelow(Level columnLevel, BlockPos pos) {
        BlockPos current = pos;

        while (columnLevel.getBlockEntity(current) instanceof SinglesStackBE be && be.isEmpty()) {
            columnLevel.setBlock(current, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
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

    public void syncToClients() {
        if (level != null) {
            BlockState state = getBlockState();
            level.sendBlockUpdated(getBlockPos(), state, state, Block.UPDATE_ALL);
        }
    }

    /**
     * Opens a run of edits that should publish as one. Per-slot sync is held back and the block
     * remembers whether anything actually changed, so {@link #endBatch()} can settle only the
     * blocks a column-wide pass really touched. The gravity paths hold sync back themselves and
     * publish through their own route, so a batch starts from a clean slate rather than inheriting
     * what one of them last touched.
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
     * Records that this block owes a publication, and asks the column to schedule the pass that
     * pays it.
     *
     * <p>Publishing a content change costs a block entity update packet, a walk of the whole column
     * for the comparator, and a light recompute. A cell holds exactly one item, so a caller moving a
     * stack through the capability calls the handler once per item, and one removal draws an item
     * down out of every block above it; deferring to a block tick on the column's bottom is what
     * keeps either from paying that at each step. A Storage pile defers its settle for the same
     * reason.
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
     * Pays what a deferred edit owes this block: contents to clients and the emitted light level.
     * The comparator value belongs to the column rather than to one of its blocks, so
     * {@link SinglesColumn#publishPending()} settles that once for the whole run.
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
    public void load(CompoundTag tag) {
        super.load(tag);
        if (tag.contains("Items")) {
            items.deserializeNBT(tag.getCompound("Items"));
        }
        if (tag.contains("Rotation")) {
            rotation = tag.getInt("Rotation");
        }
        if (tag.contains("CubeRotations")) {
            int[] loaded = tag.getIntArray("CubeRotations");
            if (loaded.length == SLOTS) {
                cubeRotations = loaded.clone();
            }
        }
        cachedShape = null;
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.put("Items", items.serializeNBT());
        tag.putInt("Rotation", rotation);
        // IntArrayTag holds the array it is given, and an integrated server hands its update
        // packets to the client unserialized: both sides must get their own copy, or the client
        // renders the server's in-progress rotations against its own not-yet-updated items.
        tag.putIntArray("CubeRotations", cubeRotations.clone());
    }

    @Nonnull
    @Override
    public <T> LazyOptional<T> getCapability(@Nonnull Capability<T> cap, @Nullable Direction side) {
        if (cap == ForgeCapabilities.ITEM_HANDLER) return itemsCap.cast();
        return super.getCapability(cap, side);
    }

    /** This block's 64 slots, for callers that already hold the block entity. */
    public IItemHandler getItems() {
        return items;
    }

    @Override
    public void invalidateCaps() {
        super.invalidateCaps();
        itemsCap.invalidate();
    }

    @Override
    public void reviveCaps() {
        super.reviveCaps();
        itemsCap = LazyOptional.of(() -> new SinglesColumnHandler(this));
    }

    @Override
    public ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public CompoundTag getUpdateTag() {
        CompoundTag tag = new CompoundTag();
        saveAdditional(tag);
        return tag;
    }

    @Override
    public void onDataPacket(Connection net, ClientboundBlockEntityDataPacket pkt) {
        handleUpdateTag(pkt.getTag());
    }
}
