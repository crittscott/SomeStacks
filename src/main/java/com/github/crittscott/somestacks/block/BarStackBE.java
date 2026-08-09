package com.github.crittscott.somestacks.block;

import com.github.crittscott.somestacks.ModRegistry;
import com.github.crittscott.somestacks.ServerConfig;
import com.github.crittscott.somestacks.server.Protection;
import com.github.crittscott.somestacks.util.BarCubeIdx;
import com.github.crittscott.somestacks.util.ItemOps;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
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
import java.util.Arrays;

/**
 * One Bar Stack: 64 bars laid in eight alternating layers of eight.
 *
 * <p>A slot index is a position, not a place in a bag, and every bar must rest on the layer beneath
 * it or on the seam with the Bar Stack below. A vertical run of these is a {@link BarColumn}, which
 * is what automation addresses; this class owns one block's slots, its shape, and the player-facing
 * cascade that drops whatever an extracted bar was holding up.
 */
public class BarStackBE extends BlockEntity {
    /** Positions in one block. The column's flat range is this times its height. */
    public static final int SLOTS = 64;

    private static final String TAG_ITEMS = "Items";

    private VoxelShape cachedShape = null;
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

    /**
     * Set when a cascade is removing this block, so {@link BarStackBlock#onRemove} knows the
     * column above is already being walked and does not start a second collapse of it.
     */
    private boolean removedByCascade = false;

    /** The column resolved for this block, good for the tick it was taken on. See {@link #column()}. */
    private BarColumn cachedColumn;
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
            return isValidBarItem(stack);
        }
    };
    private LazyOptional<IItemHandler> itemsCap = LazyOptional.of(() -> new BarColumnHandler(this));

    public BarStackBE(BlockPos pos, BlockState state) {
        super(ModRegistry.BAR_STACK_BE.get(), pos, state);
    }

    /**
     * Whether a Bar Stack accepts this item, the single decision point every deposit, column
     * insertion and capability path consults. Ingot-ness is the server's {@code ingot_tags} list
     * resolved against the loaded item tags, so an admin widens or narrows it without a data pack;
     * because the Singles rule is this rule's complement, widening it narrows Singles by as much.
     */
    public static boolean isValidBarItem(ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        }
        if (ItemOps.isItemFromDisabledMod(stack)) {
            return false;
        }
        return ServerConfig.isIngotItem(stack.getItem());
    }

    /**
     * The column this block belongs to, or null on the client and for a block being removed.
     *
     * <p>Resolved once a tick and held, because a machine reading this block's item handler resolves
     * the column once per slot it walks. See {@link StorageStackBE#pile()} for the full reasoning.
     */
    @Nullable
    public BarColumn column() {
        if (level == null || level.isClientSide) {
            return null;
        }

        long now = level.getGameTime();
        if (cachedColumn != null && cachedColumnTick == now) {
            return cachedColumn;
        }

        cachedColumn = BarColumn.resolve(level, getBlockPos());
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

    /** Builds the union of the occupied bars' boxes. */
    public VoxelShape computeShape() {
        VoxelShape shape = Shapes.empty();

        for (int i = 0; i < items.getSlots(); i++) {
            ItemStack stack = items.getStackInSlot(i);
            if (!stack.isEmpty()) {
                shape = Shapes.or(shape, BarCubeIdx.shapeFor(i));
            }
        }

        return shape;
    }

    /**
     * Returns the occupied-bar shape, rebuilding it only after contents invalidate the cache. Shape
     * queries occur frequently enough that they must not rebuild it unconditionally.
     */
    public VoxelShape getCachedShape() {
        if (cachedShape == null) {
            cachedShape = computeShape();
        }
        return cachedShape;
    }

    /**
     * Puts one bar from the given stack into the named position, if that position is empty, valid,
     * and overlapping a bar below. Refuses rather than redirecting the item elsewhere, so a caller
     * walking positions from the bottom fills the supported ones in order.
     *
     * @return whether the bar moved; the hand shrinks by one only then
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

        if (!isValidBarItem(fromHand)) {
            return false;
        }

        if (!BarCubeIdx.isGrounded(index, items, seamBeneath())) {
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
     * The player's extraction: takes one bar and lets go of whatever it was holding up. Automation
     * takes a different path — see {@link BarColumn#extract}, which backfills instead.
     */
    public ItemStack extractAt(int index) {
        if (index < 0 || index >= SLOTS) {
            return ItemStack.EMPTY;
        }

        boolean[] topBefore = BarCubeIdx.topLayerOccupancy(items);
        BarDropBatch drops = new BarDropBatch();

        ItemStack extracted;
        beginBatch();
        try {
            extracted = items.extractItem(index, 1, false);

            if (!extracted.isEmpty() && level != null && !level.isClientSide) {
                cascadeFrom(level, this, seamBeneath(), topBefore, drops);
            }
        } finally {
            endBatch();
        }

        if (level != null) {
            drops.spawn(level);
        }
        return extracted;
    }

    /**
     * Writes a bar the column already holds into an empty position, without the validity test an
     * insertion runs. The caller owns the bar and the position it goes to; see
     * {@link BarColumn#extract}, the one place a bar changes position in place, for why validity has
     * no say in it.
     */
    void relocateInto(int index, ItemStack bar) {
        items.setStackInSlot(index, bar);
    }

    /**
     * The support this block's bottom layer rests on: the top-layer occupancy of the Bar Stack
     * directly below, or null when this block stands on the world instead of on another Bar Stack.
     */
    @Nullable
    private boolean[] seamBeneath() {
        if (level != null && level.getBlockEntity(getBlockPos().below()) instanceof BarStackBE below) {
            return BarCubeIdx.topLayerOccupancy(below.items);
        }
        return null;
    }

    /**
     * Settles {@code start} against the seam beneath it, then carries the result up the column so a
     * vertical run of Bar Stacks behaves as one stack. Support crosses the seam per footprint, so a
     * block above loses only the bars whose support went away rather than collapsing wholesale. Only
     * the top layer can hold up the block above, so a block whose top layer survives intact ends the
     * walk. A block emptied along the way removes itself and passes on its now-empty top layer,
     * which is why an emptied or vanished block needs no case of its own: nothing overlaps an empty
     * seam. {@code topBefore} is {@code start}'s top layer as it stood before the edit that prompted
     * the settle, which the edit itself may already have changed.
     */
    static void cascadeFrom(Level columnLevel, BarStackBE start,
                            @Nullable boolean[] seamBelow, boolean[] topBefore,
                            BarDropBatch drops) {
        BarStackBE be = start;
        boolean[] seam = seamBelow;
        boolean[] before = topBefore;

        while (true) {
            be.beginBatch();
            try {
                be.dropUnsupported(seam, drops);
            } finally {
                be.endBatch();
            }

            boolean[] after = BarCubeIdx.topLayerOccupancy(be.items);
            BlockPos abovePos = be.getBlockPos().above();

            // A refused removal leaves an empty block standing, and the walk carries on regardless:
            // an empty seam holds nothing up either way, so the bars above still come down. Only
            // the block's going is protection's to decide.
            //
            // The flag is raised first because the removal runs onRemove, which reads it to know
            // the cascade is already walking its own way up, and lowered again if nothing went.
            if (be.isEmpty() && columnLevel instanceof ServerLevel serverLevel) {
                be.removedByCascade = true;
                if (!Protection.removeChecked(serverLevel, be.getBlockPos())) {
                    be.removedByCascade = false;
                }
            }

            if (Arrays.equals(before, after)) {
                return;
            }

            if (!(columnLevel.getBlockEntity(abovePos) instanceof BarStackBE above)) {
                return;
            }

            be = above;
            seam = after;
            before = BarCubeIdx.topLayerOccupancy(above.items);
        }
    }

    /**
     * A Bar Stack has gone from beneath {@code removed}, so the column above it has lost the seam it
     * stood on. Carries an empty seam upward, which is the same thing a cascade hands on when it
     * empties a block: nothing overlaps it, so the column comes down.
     *
     * <p>Server only, as the extraction route into the cascade is. A client reaches this while
     * applying the server's own removal, and would go on to empty block entities and set blocks to
     * air on its own authority — deciding a collapse the server has already decided and is sending.
     * The re-entrancy flag a cascade sets is runtime state that is never synced, so a client could
     * not even tell it was inside one.
     */
    static void collapseAbove(Level level, BlockPos removed, BarDropBatch drops) {
        if (level.isClientSide) {
            return;
        }
        if (level.getBlockEntity(removed.above()) instanceof BarStackBE above) {
            cascadeFrom(level, above, BarCubeIdx.emptySeam(),
                    BarCubeIdx.topLayerOccupancy(above.items), drops);
        }
    }

    boolean wasRemovedByCascade() {
        return removedByCascade;
    }

    /**
     * Drops every bar this block leaves without support. Slot index is layer-major and a bar is
     * supported only by the layer directly beneath it, so one ascending pass settles the block: by
     * the time a layer is reached, the layer it rests on is final, whether that is the layer below
     * it here or the seam. Callers batch; this does not publish.
     */
    private void dropUnsupported(@Nullable boolean[] seamBelow, BarDropBatch drops) {
        boolean[] occupancy = BarCubeIdx.occupancyOf(items);

        for (int i = 0; i < SLOTS; i++) {
            if (!occupancy[i] || BarCubeIdx.isGroundedIn(occupancy, i, seamBelow)) {
                continue;
            }

            ItemStack removed = items.extractItem(i, 1, false);
            if (!removed.isEmpty()) {
                occupancy[i] = false;
                drops.add(getBlockPos(), removed);
            }
        }
    }

    public boolean isEmpty() {
        return ItemOps.isHandlerEmpty(items);
    }

    /** Synchronizes this block's contents to tracking clients. */
    public void syncToClients() {
        if (level != null) {
            BlockState state = getBlockState();
            level.sendBlockUpdated(getBlockPos(), state, state, Block.UPDATE_ALL);
        }
    }

    /**
     * Opens a batch of edits that should publish once. Per-slot synchronization is suppressed, and
     * {@link #endBatch()} schedules publication only if this block changed.
     *
     * <p>Batches nest here, so unlike Storage and Singles this does not clear {@code batchTouched}:
     * {@link #extractAt} opens one, takes a bar, and calls {@link #cascadeFrom}, which opens another
     * on this same block. The outer change flag must survive the nested batch even when the cascade
     * removes nothing else, or clients continue rendering the extracted bar.
     */
    void beginBatch() {
        suppressSync = true;
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
     * recompute. A position holds one bar, so a caller moving a stack calls the handler once per
     * bar; deferring to a block tick on the column's bottom keeps one stack from costing all of
     * that sixty-four times over.
     */
    private void schedulePublish() {
        if (level == null || level.isClientSide) {
            return;
        }
        publishPending = true;

        BarColumn column = column();
        if (column != null) {
            column.markDirty();
        }
    }

    /**
     * Publishes this block's deferred contents and emitted light level. Comparator output belongs
     * to the column and is published once for the whole run by {@link BarColumn#publishPending()}.
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
        if (state.getValue(BarStackBlock.LIGHT_LEVEL) != newLight) {
            columnLevel.setBlock(getBlockPos(), state.setValue(BarStackBlock.LIGHT_LEVEL, newLight), Block.UPDATE_ALL);
        }
        return true;
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        if (tag.contains(TAG_ITEMS)) {
            items.deserializeNBT(tag.getCompound(TAG_ITEMS));
        }
        cachedShape = null;
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.put(TAG_ITEMS, items.serializeNBT());
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
        itemsCap = LazyOptional.of(() -> new BarColumnHandler(this));
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
