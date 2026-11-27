package com.github.crittscott.somestacks.block;

import com.github.crittscott.somestacks.ServerLifecycle;
import com.github.crittscott.somestacks.util.CubeGrid;
import com.github.crittscott.somestacks.util.ItemOps;
import com.github.crittscott.somestacks.util.StackItemStorage;
import com.github.crittscott.somestacks.util.ViewRay;

import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.AABB;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Local inventory, persistence, synchronization, lighting, batching, and run caching for stacks.
 */
public abstract class StackBlockEntity extends BlockEntity {
    private static final String TAG_DATA_VERSION = "DataVersion";

    protected final StackItemStorage items;
    @Nullable private StackItemStorage.LoadResult pendingLoadResult;
    private int batchDepth;
    private boolean batchTouched;
    @Nullable private ServerPlayer breakingPlayer;
    private boolean publishPending;
    private int publishedSignal = -1;
    private double comparatorContribution;
    private final double[] slotContributions;
    private double contributionCorrection;
    private int occupiedSlots;
    private int fullSlots;
    private StackRun<?> cachedRun;
    private long cachedRunTick = Long.MIN_VALUE;

    /** Creates local storage with subtype admission and a deferred publication callback. */
    protected StackBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state, int slots) {
        super(type, pos, state);
        slotContributions = new double[slots];
        items =
                new StackItemStorage(slots) {
                    @Override
                    protected void onContentsChanged(int slot) {
                        setChanged();
                        updateComparatorContribution(slot);
                        onLocalContentsChanged(slot);
                        if (isBatching()) {
                            batchTouched = true;
                        } else {
                            schedulePublish();
                        }
                    }

                    @Override
                    public int getSlotLimit(int slot) {
                        return slotLimit();
                    }

                    @Override
                    public boolean isItemValid(int slot, @Nonnull ItemStack stack) {
                        return admits(stack);
                    }
                };
    }

    /**
     * New-item admission for players' and automation's insertions alike, excluding support and
     * growth feasibility; saved contents and internal moves bypass this check.
     */
    public abstract boolean admits(ItemStack stack);

    /** Block-local box of one cell at this block's current layout. */
    protected abstract AABB cellBox(int index);

    /**
     * Whether a player's deposit along {@code ray} has somewhere to go in this block. A top-face
     * click that has none places a new block above instead.
     */
    public abstract boolean acceptsDepositAlong(ViewRay ray);

    /**
     * A player's deposit from {@code fromHand}, aimed along {@code ray}. Admission, support, and
     * any growth are checked here; the caller has already applied the player's item policy.
     *
     * @return how many items moved; {@code fromHand} shrinks by that many
     */
    public abstract int depositFromHand(ViewRay ray, ItemStack fromHand, ServerPlayer player);

    /**
     * A player's extraction from cell {@code index} into a main hand holding {@code hand}, taking
     * only what that hand can accept and charging any cleanup to {@code player}.
     *
     * @return what was taken, or empty when nothing moved
     */
    public abstract ItemStack extractIntoHand(int index, ItemStack hand, ServerPlayer player);

    /** Resolves the server run without consulting this entity's per-tick cache. */
    protected abstract StackRun<?> resolveRun(ServerLevel serverLevel);

    /** The cell a deposit along {@code ray} targets; see {@link ViewRay#depositTarget}. */
    public final int depositTarget(ViewRay ray) {
        return ray.depositTarget(getBlockPos(), items.getSlots(), this::cellBox, this::isOccupied);
    }

    /** The cell a player's extraction along {@code ray} takes from, or -1 on a miss. */
    public final int extractionTarget(ViewRay ray) {
        return ray.nearestOccupied(
                getBlockPos(), items.getSlots(), this::cellBox, this::isOccupied);
    }

    private boolean isOccupied(int index) {
        return !items.getStackInSlot(index).isEmpty();
    }

    /** Current server run, or null on the client or when the entity is removed. */
    @Nullable
    public final StackRun<?> itemRun() {
        if (isRemoved() || !(level instanceof ServerLevel serverLevel)) {
            return null;
        }
        long now = serverLevel.getGameTime();
        if (cachedRun != null && cachedRunTick == now) {
            return cachedRun;
        }
        cachedRun = resolveRun(serverLevel);
        cachedRunTick = now;
        return cachedRun;
    }

    /** Run positions including headroom, or local slots when no server run is available. */
    public final int advertisedItemSlots() {
        StackRun<?> run = itemRun();
        return run != null ? run.advertisedSlots() : items.getSlots();
    }

    /**
     * Contents at an advertised position: the run's slot when a server run resolves, otherwise this
     * block's own local slot, and empty for headroom or a position outside either. Every loader
     * adapter reads positions through this.
     */
    public final ItemStack advertisedStack(int slot) {
        StackRun<?> run = itemRun();
        if (run != null) {
            return run.getSlot(slot);
        }
        return slot >= 0 && slot < items.getSlots() ? items.getStackInSlot(slot) : ItemStack.EMPTY;
    }

    /** Invalidates bounds after a structural edit within the same tick. */
    final void invalidateRunCache() {
        cachedRun = null;
    }

    /** Installs the same resolved run on each member without another world walk. */
    final void cacheRun(StackRun<?> run, long tick) {
        cachedRun = run;
        cachedRunTick = tick;
    }

    /**
     * Records the player whose break is about to remove this block, so the removal hook can charge
     * the cleanup it causes to that player. Only a player's break records one; explosions,
     * commands, and other removals leave it null.
     */
    final void noteBreakingPlayer(ServerPlayer player) {
        breakingPlayer = player;
    }

    /** Takes and clears the player recorded for this block's removal, or null when none was. */
    @Nullable
    final ServerPlayer takeBreakingPlayer() {
        ServerPlayer player = breakingPlayer;
        breakingPlayer = null;
        return player;
    }

    /**
     * Per-position capacity before the item's own stack limit. A limit of one marks a structural
     * stack, whose every position holds a single item.
     */
    public int slotLimit() {
        return StackItemStorage.DEFAULT_SLOT_LIMIT;
    }

    /** Local numerator of the run's fill fraction, refreshed on mutation and load. */
    final double comparatorContribution() {
        return comparatorContribution;
    }

    private void refreshComparatorContribution() {
        comparatorContribution = 0.0;
        contributionCorrection = 0.0;
        occupiedSlots = 0;
        fullSlots = 0;
        for (int slot = 0; slot < items.getSlots(); slot++) {
            slotContributions[slot] = 0.0;
            updateComparatorContribution(slot);
        }
    }

    private void updateComparatorContribution(int slot) {
        ItemStack stack = items.getStackInSlot(slot);
        double previous = slotContributions[slot];
        double current =
                stack.isEmpty()
                        ? 0.0
                        : slotLimit() == 1
                                ? 1.0
                                : (double) stack.getCount() / stack.getMaxStackSize();
        slotContributions[slot] = current;
        occupiedSlots += (current != 0.0 ? 1 : 0) - (previous != 0.0 ? 1 : 0);
        fullSlots += (current == 1.0 ? 1 : 0) - (previous == 1.0 ? 1 : 0);

        // Compensated addition limits drift from repeated fractional slot changes.
        double delta = (current - previous) - contributionCorrection;
        double total = comparatorContribution + delta;
        contributionCorrection = (total - comparatorContribution) - delta;
        comparatorContribution = total;
        // Empty and completely full inventories must retain exact comparator endpoints.
        if (occupiedSlots == 0 || fullSlots == slotContributions.length) {
            comparatorContribution = occupiedSlots == 0 ? 0.0 : slotContributions.length;
            contributionCorrection = 0.0;
        }
    }

    /** Refreshes subtype-derived state after a local slot changes, including inside a batch. */
    protected void onLocalContentsChanged(int slot) {}

    /**
     * Loads subtype metadata and derived state after inventory replacement, without publication.
     * Client data has already passed {@link #isValidClientStackData(CompoundTag)}.
     */
    protected void loadStackData(CompoundTag tag) {}

    /** Checks type-specific update data before inventory or metadata is changed. */
    protected boolean isValidClientStackData(CompoundTag tag) {
        return true;
    }

    /**
     * Adds subtype metadata to both disk saves and client update tags; private fields are removed
     * in {@link #stripServerOnlyUpdateData(CompoundTag)} before transmission.
     */
    protected void saveStackData(CompoundTag tag) {}

    /** Removes server-only state before a saved tag is sent as a client update. */
    protected void stripServerOnlyUpdateData(CompoundTag tag) {}

    /** Whether all local slots are empty. */
    public final boolean isEmpty() {
        return ItemOps.isHandlerEmpty(items);
    }

    /** Mutable block-local storage; indices exclude all other blocks in the run. */
    public final StackItemStorage getItems() {
        return items;
    }

    /**
     * The block's layout rotation in the grid's counterclockwise quarter turns, read from its
     * horizontal facing so structure rotation and mirroring turn the layout with the block.
     */
    public final int getRotation() {
        BlockState state = getBlockState();
        return state.hasProperty(BlockStateProperties.HORIZONTAL_FACING)
                ? rotationOf(state.getValue(BlockStateProperties.HORIZONTAL_FACING))
                : 0;
    }

    /**
     * Changes a server block's facing; the block-state update performs client synchronization. Only
     * Storage and Singles blocks, which have a facing, are rotated.
     */
    public final void setRotation(int rotation) {
        BlockState state = getBlockState();
        BlockState rotated =
                state.setValue(BlockStateProperties.HORIZONTAL_FACING, facingOf(rotation));
        if (state == rotated) {
            return;
        }
        if (level.setBlock(getBlockPos(), rotated, Block.UPDATE_ALL)) {
            setChanged();
        }
    }

    /** Maps the grid's counterclockwise quarter turns to a horizontal facing. */
    private static Direction facingOf(int rotation) {
        return switch (Math.floorMod(rotation, CubeGrid.ROTATIONS)) {
            case 1 -> Direction.WEST;
            case 2 -> Direction.SOUTH;
            case 3 -> Direction.EAST;
            default -> Direction.NORTH;
        };
    }

    /** Inverse of {@link #facingOf(int)}. */
    private static int rotationOf(Direction facing) {
        return switch (facing) {
            case WEST -> 1;
            case SOUTH -> 2;
            case EAST -> 3;
            default -> 0;
        };
    }

    /** Immediately synchronizes this entity's local contents and presentation. */
    public final void syncToClients() {
        BlockState state = getBlockState();
        level.sendBlockUpdated(getBlockPos(), state, state, Block.UPDATE_ALL);
    }

    /** Records a comparator publication, returning whether neighbors need notification. */
    final boolean exchangePublishedSignal(int signal) {
        if (publishedSignal == signal) {
            return false;
        }
        publishedSignal = signal;
        return true;
    }

    /** Begins a nesting-safe batch; each call must pair with an end method. */
    final void beginBatch() {
        if (batchDepth++ == 0) {
            batchTouched = false;
        }
    }

    /** Ends a batch and schedules publication only when the outer touched batch ends. */
    final void endBatch() {
        if (--batchDepth == 0 && batchTouched) {
            batchTouched = false;
            schedulePublish();
        }
    }

    /** Ends a batch inside settlement, leaving publication to the active settlement pass. */
    final void endBatchWithinSettle() {
        if (--batchDepth == 0 && batchTouched) {
            batchTouched = false;
            publishPending = true;
        }
    }

    /** Whether a local mutation batch is active. */
    protected final boolean isBatching() {
        return batchDepth > 0;
    }

    /** Marks local publication pending and coalesces a server tick on the run's base. */
    protected final void schedulePublish() {
        if (!(level instanceof ServerLevel)) {
            return;
        }
        publishPending = true;
        StackRun<?> run = itemRun();
        if (run != null) {
            run.markDirty();
        }
    }

    /** Publishes pending contents and light; returns whether any work was owed. */
    final boolean publishIfPending(ServerLevel currentLevel) {
        if (!publishPending) {
            return false;
        }
        publishPending = false;
        syncToClients();
        int light = ItemOps.calculateLightLevelFromItems(items);
        BlockState state = getBlockState();
        if (state.getValue(StackBlock.LIGHT_LEVEL) != light) {
            currentLevel.setBlock(
                    getBlockPos(), state.setValue(StackBlock.LIGHT_LEVEL, light), Block.UPDATE_ALL);
        }
        return true;
    }

    @Override
    public void setLevel(Level level) {
        super.setLevel(level);
        reportSavedItems();
    }

    /** Reports set-aside items only after server attachment supplies a dimension. */
    private void reportSavedItems() {
        if (pendingLoadResult == null || level == null) {
            return;
        }
        if (level instanceof ServerLevel serverLevel) {
            ServerLifecycle.reportSavedItems(serverLevel, getBlockPos(), pendingLoadResult);
        }
        pendingLoadResult = null;
    }

    /**
     * Reads a disk save, or on the client an update tag. Every loader's chunk and block-entity
     * update path on the client reaches this method with the client level already attached.
     */
    @Override
    public final void load(CompoundTag tag) {
        if (level != null && level.isClientSide) {
            loadClientUpdate(tag);
            return;
        }
        super.load(tag);
        pendingLoadResult = null;
        if (tag.contains(StackItemStorage.TAG_ITEMS)) {
            pendingLoadResult = items.deserializeNBT(tag.getCompound(StackItemStorage.TAG_ITEMS));
            reportSavedItems();
        }
        loadStackData(tag);
        refreshComparatorContribution();
        invalidateRunCache();
    }

    /**
     * Applies a server update only with the current data version, a well-formed inventory without
     * recovery data, and valid subtype metadata. Unrelated keys are ignored; a rejected tag changes
     * nothing.
     */
    public final boolean loadClientUpdate(CompoundTag tag) {
        if (!tag.contains(TAG_DATA_VERSION, Tag.TAG_INT)
                || tag.getInt(TAG_DATA_VERSION)
                        != SharedConstants.getCurrentVersion().getDataVersion().getVersion()
                || !tag.contains(StackItemStorage.TAG_ITEMS, Tag.TAG_COMPOUND)
                || !isValidClientStackData(tag)
                || !items.deserializeClientUpdate(tag.getCompound(StackItemStorage.TAG_ITEMS))) {
            return false;
        }
        loadStackData(tag);
        refreshComparatorContribution();
        return true;
    }

    @Override
    protected final void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        NbtUtils.addCurrentDataVersion(tag);
        tag.put(StackItemStorage.TAG_ITEMS, items.serializeNBT());
        saveStackData(tag);
    }

    @Override
    public final ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public final CompoundTag getUpdateTag() {
        CompoundTag tag = new CompoundTag();
        saveAdditional(tag);
        CompoundTag storage = tag.getCompound(StackItemStorage.TAG_ITEMS);
        StackItemStorage.stripSetAside(storage);
        StackItemStorage.stripUndrawnItemData(storage);
        stripServerOnlyUpdateData(tag);
        return tag;
    }
}
