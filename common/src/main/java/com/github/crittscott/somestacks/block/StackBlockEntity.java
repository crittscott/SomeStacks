package com.github.crittscott.somestacks.block;

import com.github.crittscott.somestacks.SomeStacksServer;
import com.github.crittscott.somestacks.util.ItemOps;
import com.github.crittscott.somestacks.util.QuarterTurns;
import com.github.crittscott.somestacks.util.StackItemStorage;
import com.github.crittscott.somestacks.util.ViewRay;

import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
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
import net.minecraft.world.phys.AABB;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/** Local storage, persistence, synchronization, lighting, and batching shared by every stack. */
public abstract class StackBlockEntity extends BlockEntity {
    static final String TAG_ITEMS = "Items";

    protected final StackItemStorage items;
    @Nullable private ServerPlayer breakingPlayer;
    @Nullable private Integer pendingBlockRotation;
    @Nullable private StackItemStorage.LoadResult pendingLoadResult;
    private int batchDepth;
    private boolean batchTouched;
    private boolean publishPending;
    private int publishedSignal = -1;
    private double comparatorContribution;
    private final double[] slotContributions;
    private boolean comparatorDirty;
    private StackRunItemAccess cachedRun;
    private long cachedRunTick = Long.MIN_VALUE;

    /**
     * Creates fixed local storage whose mutations refresh fill, notify the subtype, and batch
     * publication.
     */
    protected StackBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state, int slots) {
        super(type, pos, state);
        slotContributions = new double[slots];
        items =
                new StackItemStorage(slots) {
                    @Override
                    protected void onContentsChanged(int slot) {
                        setChanged();
                        refreshSlotContribution(slot);
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
     * Admission for new player and automation inserts, including current namespace and type
     * policy. Existing saved or internally relocated items bypass this check; it selects no slot
     * and checks neither support nor growth authority.
     */
    public abstract boolean admits(ItemStack stack);

    /** Resolves the current server-side run without consulting the per-tick cache. */
    protected abstract StackRunItemAccess resolveRun(ServerLevel serverLevel);

    /** The block-local box of the cell at {@code slot}, at this block's current layout. */
    protected abstract AABB cellBox(int slot);

    /**
     * Whether a player's modified-item click on this block's top face along {@code ray} deposits
     * here, rather than placing a new stack above.
     */
    public abstract boolean acceptsDepositAlong(ViewRay ray);

    /**
     * A player's deposit from {@code hand} into the cell or run {@code ray} targets, shrinking
     * {@code hand} by what moved. Admission, support, and growth are this method's to check.
     *
     * @return how many items moved
     */
    public abstract int depositFromHand(ViewRay ray, ItemStack hand, ServerPlayer player);

    /**
     * A player's extraction from the occupied cell at {@code index} into a hand currently holding
     * {@code hand}, taking only what that hand can hold. Structural cleanup is attributed to
     * {@code player}.
     *
     * @return what was taken, or an empty stack
     */
    public abstract ItemStack extractIntoHand(int index, ItemStack hand, ServerPlayer player);

    /**
     * The cell a deposit along {@code ray} fills: the last empty cell before the first occupied
     * one, or the farthest cell the ray crosses when it meets nothing occupied; -1 when it crosses
     * none.
     */
    public final int depositTarget(ViewRay ray) {
        return ray.lastEmptyBeforeOccupied(items.getSlots(), items, this::worldCellBox);
    }

    /**
     * The occupied cell {@code ray} meets first, which extraction and item rotation act on, or -1.
     */
    public final int extractionTarget(ViewRay ray) {
        return ray.nearestOccupied(items, this::worldCellBox);
    }

    private AABB worldCellBox(int slot) {
        BlockPos pos = getBlockPos();
        return cellBox(slot).move(pos.getX(), pos.getY(), pos.getZ());
    }

    /**
     * The current server-side run, or {@code null} on the client. A block whose entity is gone
     * resolves to an empty run.
     */
    @Nullable
    public final StackRunItemAccess itemRun() {
        if (!(level instanceof ServerLevel serverLevel)) {
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

    /** Drops the cached run so a structural edit is visible again within the current tick. */
    final void invalidateRunCache() {
        cachedRun = null;
    }

    /** Schedules the owning run's deferred publication pass. */
    protected final void markRunDirty() {
        StackRunItemAccess run = itemRun();
        if (run != null) {
            run.markDirty();
        }
    }

    /**
     * Per-slot capacity before the item's own stack limit, or one for positional display types.
     * Automation views report the same capacity for every run position.
     */
    public int slotLimit() {
        return StackItemStorage.DEFAULT_SLOT_LIMIT;
    }

    /**
     * Positions an automation view advertises: the run's positions, including headroom, when a
     * server run resolves, otherwise this block's local slots.
     */
    public final int advertisedSlots() {
        StackRunItemAccess run = itemRun();
        return run != null ? run.advertisedSlots() : items.getSlots();
    }

    /**
     * The read-only stack at an advertised position: the run's slot when a server run resolves,
     * otherwise this block's local slot. Headroom and invalid positions are empty.
     */
    public final ItemStack advertisedStack(int slot) {
        StackRunItemAccess run = itemRun();
        if (run != null) {
            return run.getSlot(slot);
        }
        return slot >= 0 && slot < items.getSlots() ? items.getStackInSlot(slot) : ItemStack.EMPTY;
    }

    /** Cached local numerator of the run's comparator fill fraction. */
    final double comparatorContribution() {
        if (comparatorDirty) {
            double sum = 0.0;
            for (double contribution : slotContributions) {
                sum += contribution;
            }
            comparatorContribution = sum;
            comparatorDirty = false;
        }
        return comparatorContribution;
    }

    /**
     * Updates only the changed slot; summing cached fractions on demand avoids cumulative rounding
     * drift.
     */
    private void refreshSlotContribution(int slot) {
        ItemStack stack = items.getStackInSlot(slot);
        slotContributions[slot] =
                stack.isEmpty()
                        ? 0.0
                        : slotLimit() == 1
                                ? 1.0
                                : (double) stack.getCount() / stack.getMaxStackSize();
        comparatorDirty = true;
    }

    private void refreshComparatorContribution() {
        for (int slot = 0; slot < items.getSlots(); slot++) {
            refreshSlotContribution(slot);
        }
        comparatorContribution();
    }

    /**
     * Invalidates subtype-derived local state after a mutation to {@code 0 <= slot <
     * getItems().getSlots()}. Runs on either side after dirty/fill updates, even inside a batch,
     * before deferred publication. Disk and client snapshot loads bypass this hook and must refresh
     * subtype state in loadStackData.
     */
    protected void onLocalContentsChanged(int slot) {}

    /**
     * Loads subtype metadata and refreshes derived state after inventory replacement. Disk data is
     * already migrated; client data is already validated. Runs on either side without publication.
     */
    protected void loadStackData(CompoundTag tag, HolderLookup.Provider registries) {}

    /** Checks type-specific update data before inventory or metadata is changed. */
    protected boolean isValidClientStackData(CompoundTag tag) {
        return true;
    }

    /**
     * Adds current subtype metadata after version and inventory serialization. Used for both disk
     * saves and update tags; remove private fields in stripServerOnlyUpdateData before
     * transmission.
     */
    protected void saveStackData(CompoundTag tag, HolderLookup.Provider registries) {}

    /** Removes server-only state before a saved tag is sent as a client update. */
    protected void stripServerOnlyUpdateData(CompoundTag tag) {}

    /**
     * Whether every live local slot is empty; retained disk entries do not count as live contents.
     */
    public final boolean isEmpty() {
        return ItemOps.isHandlerEmpty(items);
    }

    /**
     * Mutable local storage, indexed from zero to its slot count exclusively, not the whole run.
     */
    public final StackItemStorage getItems() {
        return items;
    }

    /** The outer block's layout rotation, expressed in the grid's quarter-turn coordinates. */
    protected final int blockRotation() {
        applyPendingBlockRotation();
        BlockState state = getBlockState();
        return state.hasProperty(StackBlock.HORIZONTAL_FACING)
                ? QuarterTurns.fromDirection(state.getValue(StackBlock.HORIZONTAL_FACING))
                : 0;
    }

    /** Changes the outer block's facing; the block-state update performs client synchronization. */
    protected final void setBlockRotation(int rotation) {
        pendingBlockRotation = QuarterTurns.normalize(rotation);
        applyPendingBlockRotation();
    }

    /** Immediately sends this block's local render state to tracking clients. */
    public final void syncToClients() {
        BlockState state = getBlockState();
        level.sendBlockUpdated(getBlockPos(), state, state, Block.UPDATE_ALL);
    }

    /**
     * Records the player whose break is about to remove this block, so cleanup the removal causes
     * is attributed to that player. Only {@link StackBlock#playerWillDestroy} calls this.
     */
    final void noteBreakingPlayer(ServerPlayer player) {
        breakingPlayer = player;
    }

    /**
     * Takes the player recorded by {@link #noteBreakingPlayer}, or null when the removal was not a
     * player's break (explosions, commands, other mods), which leaves cleanup to automation.
     */
    @Nullable
    final ServerPlayer takeBreakingPlayer() {
        ServerPlayer player = breakingPlayer;
        breakingPlayer = null;
        return player;
    }

    /** Records the last published comparator signal; returns true only when it changed. */
    final boolean exchangePublishedSignal(int signal) {
        if (publishedSignal == signal) {
            return false;
        }
        publishedSignal = signal;
        return true;
    }

    /** Begins a nesting-safe mutation batch; every call must pair with exactly one end method. */
    final void beginBatch() {
        if (batchDepth++ == 0) {
            batchTouched = false;
        }
    }

    /**
     * Ends a batch, scheduling one server publication pass when the outermost touched batch ends.
     */
    final void endBatch() {
        if (--batchDepth == 0 && batchTouched) {
            batchTouched = false;
            schedulePublish();
        }
    }

    /** Ends a batch whose caller will publish the resulting structural edit itself. */
    final void endBatchWithoutPublish() {
        if (--batchDepth == 0) {
            batchTouched = false;
        }
    }

    /** Ends a batch inside an active settle, leaving publication to that settle. */
    final void endBatchWithinSettle() {
        if (--batchDepth == 0 && batchTouched) {
            batchTouched = false;
            publishPending = true;
        }
    }

    /**
     * Whether a mutation batch is active, including nested batches; derived-state hooks still run.
     */
    protected final boolean isBatching() {
        return batchDepth > 0;
    }

    /**
     * Marks local publication pending and coalesces a run tick; unattached/client entities do
     * nothing.
     */
    protected final void schedulePublish() {
        if (level == null || level.isClientSide) {
            return;
        }
        publishPending = true;
        markRunDirty();
    }

    @Override
    public void setLevel(Level level) {
        super.setLevel(level);
        applyPendingBlockRotation();
        reportSavedItems();
    }

    /** Reports disk recovery only after server attachment supplies a dimension. */
    private void reportSavedItems() {
        if (pendingLoadResult == null || level == null) return;
        if (level instanceof ServerLevel serverLevel) {
            SomeStacksServer.reportSavedItems(serverLevel, getBlockPos(), pendingLoadResult);
        }
        pendingLoadResult = null;
    }

    /** Applies a requested or migrated rotation once the block belongs to a server level. */
    private void applyPendingBlockRotation() {
        if (pendingBlockRotation == null || level == null || level.isClientSide) {
            return;
        }
        BlockState state = getBlockState();
        if (!state.hasProperty(StackBlock.HORIZONTAL_FACING)) {
            pendingBlockRotation = null;
            return;
        }
        BlockState migrated =
                state.setValue(
                        StackBlock.HORIZONTAL_FACING,
                        QuarterTurns.toDirection(pendingBlockRotation));
        if (state == migrated || level.setBlock(getBlockPos(), migrated, Block.UPDATE_ALL)) {
            pendingBlockRotation = null;
            setChanged();
        }
    }

    /**
     * Publishes pending contents and light on the owning server level; returns whether it did work.
     */
    final boolean publishIfPending(ServerLevel currentLevel) {
        if (!publishPending) {
            return false;
        }
        publishPending = false;
        syncToClients();

        int newLight = ItemOps.calculateLightLevelFromItems(items);
        BlockState state = getBlockState();
        if (state.getValue(StackBlock.LIGHT_LEVEL) != newLight) {
            currentLevel.setBlock(
                    getBlockPos(),
                    state.setValue(StackBlock.LIGHT_LEVEL, newLight),
                    Block.UPDATE_ALL);
        }
        return true;
    }

    @Override
    protected final void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        if (level != null && level.isClientSide) {
            loadClientUpdate(tag, registries);
            return;
        }
        super.loadAdditional(tag, registries);
        pendingLoadResult = null;
        if (StackDataMigration.upgrade(tag, registries)) {
            setChanged();
        }
        Integer legacyBlockRotation = StackDataMigration.takeLegacyBlockRotation(tag);
        if (legacyBlockRotation != null) {
            pendingBlockRotation = legacyBlockRotation;
            applyPendingBlockRotation();
            setChanged();
        }
        if (tag.contains(TAG_ITEMS)) {
            pendingLoadResult = items.deserializeNBT(registries, tag.getCompound(TAG_ITEMS));
            reportSavedItems();
        }
        loadStackData(tag, registries);
        refreshComparatorContribution();
    }

    /**
     * Forge/NeoForge chunk-update hook; Fabric updates reach the client branch of loadAdditional.
     */
    public final void handleUpdateTag(CompoundTag tag, HolderLookup.Provider registries) {
        loadClientUpdate(tag, registries);
    }

    /** Rejects malformed wire data without migration, recovery retention, or logging. */
    public final boolean loadClientUpdate(CompoundTag tag, HolderLookup.Provider registries) {
        if (!tag.contains("DataVersion", Tag.TAG_INT)
                || tag.getInt("DataVersion")
                        != SharedConstants.getCurrentVersion().getDataVersion().getVersion()
                || !tag.contains(TAG_ITEMS, Tag.TAG_COMPOUND)
                || tag.contains(StackItemStorage.TAG_SET_ASIDE)
                || tag.contains("Permanent")
                || tag.contains("Rotation")
                || !isValidClientStackData(tag)
                || !items.deserializeClientUpdate(registries, tag.getCompound(TAG_ITEMS))) {
            return false;
        }
        pendingBlockRotation = null;
        loadStackData(tag, registries);
        refreshComparatorContribution();
        return true;
    }

    @Override
    protected final void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        saveWith(tag, registries, items.serializeNBT(registries));
    }

    private void saveWith(
            CompoundTag tag, HolderLookup.Provider registries, CompoundTag serializedItems) {
        StackDataMigration.stampVersion(tag);
        tag.put(TAG_ITEMS, serializedItems);
        saveStackData(tag, registries);
    }

    @Override
    public final ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public final CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        super.saveAdditional(tag, registries);
        saveWith(tag, registries, items.serializeClientUpdate(registries));
        stripServerOnlyUpdateData(tag);
        return tag;
    }
}
