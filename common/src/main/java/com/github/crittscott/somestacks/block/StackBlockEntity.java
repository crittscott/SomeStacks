package com.github.crittscott.somestacks.block;

import com.github.crittscott.somestacks.util.ItemOps;
import com.github.crittscott.somestacks.util.StackItemStorage;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.IntegerProperty;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/** Local inventory, persistence, synchronization, lighting, batching, and run caching for stacks. */
public abstract class StackBlockEntity extends BlockEntity {
    private static final String TAG_ITEMS = "Items";
    private static final int DEFAULT_SLOT_LIMIT = 64;

    protected final StackItemStorage items;
    private final IntegerProperty lightProperty;
    private int batchDepth;
    private boolean batchTouched;
    private boolean publishPending;
    private int publishedSignal = -1;
    private double comparatorContribution;
    private final double[] slotContributions;
    private double contributionCorrection;
    private int occupiedSlots;
    private int fullSlots;
    private StackRunItemAccess cachedRun;
    private long cachedRunTick = Long.MIN_VALUE;

    /** Creates local storage with subtype admission and a deferred publication callback. */
    protected StackBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state,
                               int slots, IntegerProperty lightProperty) {
        super(type, pos, state);
        this.lightProperty = lightProperty;
        slotContributions = new double[slots];
        items = new StackItemStorage(slots) {
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
                return localSlotLimit();
            }

            @Override
            public boolean isItemValid(int slot, @Nonnull ItemStack stack) {
                return isStoredItemValid(stack);
            }
        };
    }

    /** Admission for new items; saved contents and internal moves bypass this check. */
    protected abstract boolean isStoredItemValid(ItemStack stack);

    /** Resolves the server run without consulting this entity's per-tick cache. */
    protected abstract StackRunItemAccess resolveRun(ServerLevel serverLevel);

    /** Current server run, or null on the client or when the entity is removed. */
    @Nullable
    public final StackRunItemAccess itemRun() {
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

    /** Invalidates bounds after a structural edit within the same tick. */
    final void invalidateRunCache() {
        cachedRun = null;
    }

    /** Installs the same resolved run on each member without another world walk. */
    final void cacheRun(StackRunItemAccess run, long tick) {
        cachedRun = run;
        cachedRunTick = tick;
    }

    /** Capacity before the item's own stack limit. */
    protected int localSlotLimit() {
        return DEFAULT_SLOT_LIMIT;
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
        double current = stack.isEmpty() ? 0.0 : localSlotLimit() == 1 ? 1.0
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
    protected void onLocalContentsChanged(int slot) {
    }

    /** Loads subtype metadata and derived state after inventory replacement, without publication. */
    protected void loadStackData(CompoundTag tag) {
    }

    /** Adds subtype metadata to both disk saves and client update tags. */
    protected void saveStackData(CompoundTag tag) {
    }

    /** Whether all local slots are empty. */
    public final boolean isEmpty() {
        return ItemOps.isHandlerEmpty(items);
    }

    /** Mutable block-local storage; indices exclude all other blocks in the run. */
    public final StackItemStorage getItems() {
        return items;
    }

    /** Per-position automation capacity before the item's own stack limit. */
    public final int automationSlotLimit() {
        return localSlotLimit();
    }

    /** New-item automation admission, excluding support and growth feasibility. */
    public final boolean acceptsAutomation(ItemStack stack) {
        return isStoredItemValid(stack);
    }

    /** Immediately synchronizes this entity's local contents and presentation. */
    public final void syncToClients() {
        if (level != null) {
            BlockState state = getBlockState();
            level.sendBlockUpdated(getBlockPos(), state, state, Block.UPDATE_ALL);
        }
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
        StackRunItemAccess run = itemRun();
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
        if (state.getValue(lightProperty) != light) {
            currentLevel.setBlock(getBlockPos(), state.setValue(lightProperty, light), Block.UPDATE_ALL);
        }
        return true;
    }

    @Override
    public final void load(CompoundTag tag) {
        super.load(tag);
        if (tag.contains(TAG_ITEMS)) {
            items.deserializeNBT(tag.getCompound(TAG_ITEMS));
        }
        loadStackData(tag);
        refreshComparatorContribution();
        invalidateRunCache();
    }

    @Override
    protected final void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.put(TAG_ITEMS, items.serializeNBT());
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
        return tag;
    }
}
