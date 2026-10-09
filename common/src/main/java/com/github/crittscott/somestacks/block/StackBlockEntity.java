package com.github.crittscott.somestacks.block;

import com.github.crittscott.somestacks.util.ItemOps;
import com.github.crittscott.somestacks.util.QuarterTurns;
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
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/** Local storage, persistence, synchronization, lighting, and batching shared by every stack. */
public abstract class StackBlockEntity extends BlockEntity {
    static final String TAG_ITEMS = "Items";

    protected final StackItemStorage items;
    @Nullable
    private Integer pendingBlockRotation;
    private int batchDepth;
    private boolean batchTouched;
    private boolean publishPending;
    private int publishedSignal = -1;
    private double comparatorContribution;
    private StackRunItemAccess cachedRun;
    private long cachedRunTick = Long.MIN_VALUE;

    protected StackBlockEntity(
            BlockEntityType<?> type, BlockPos pos, BlockState state, int slots) {
        super(type, pos, state);
        items = new StackItemStorage(slots) {
            @Override
            protected void onContentsChanged(int slot) {
                setChanged();
                refreshComparatorContribution();
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

    protected abstract boolean isStoredItemValid(ItemStack stack);

    /** Resolves the current server-side run without consulting the per-tick cache. */
    protected abstract StackRunItemAccess resolveRun(ServerLevel serverLevel);

    /** The current server-side run, or {@code null} on the client or during removal. */
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

    protected int localSlotLimit() {
        return 64;
    }

    /** Cached local numerator of the run's comparator fill fraction. */
    final double comparatorContribution() {
        return comparatorContribution;
    }

    private void refreshComparatorContribution() {
        double sum = 0.0;
        for (int slot = 0; slot < items.getSlots(); slot++) {
            ItemStack stack = items.getStackInSlot(slot);
            if (!stack.isEmpty()) {
                sum += localSlotLimit() == 1 ? 1.0
                        : (double) stack.getCount() / stack.getMaxStackSize();
            }
        }
        comparatorContribution = sum;
    }

    protected void onLocalContentsChanged(int slot) {
    }

    protected void loadStackData(CompoundTag tag, HolderLookup.Provider registries) {
    }

    protected void saveStackData(CompoundTag tag, HolderLookup.Provider registries) {
    }

    /** Removes server-only state before a saved tag is sent as a client update. */
    protected void stripServerOnlyUpdateData(CompoundTag tag) {
    }

    public final boolean isEmpty() {
        return ItemOps.isHandlerEmpty(items);
    }

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

    public final int automationSlotLimit() {
        return localSlotLimit();
    }

    public final boolean acceptsAutomation(ItemStack stack) {
        return isStoredItemValid(stack);
    }

    public final void syncToClients() {
        if (level != null) {
            BlockState state = getBlockState();
            level.sendBlockUpdated(getBlockPos(), state, state, Block.UPDATE_ALL);
        }
    }

    final boolean exchangePublishedSignal(int signal) {
        if (publishedSignal == signal) {
            return false;
        }
        publishedSignal = signal;
        return true;
    }

    final void beginBatch() {
        if (batchDepth++ == 0) {
            batchTouched = false;
        }
    }

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

    protected final boolean isBatching() {
        return batchDepth > 0;
    }

    private void schedulePublish() {
        if (level == null || level.isClientSide) {
            return;
        }
        publishPending = true;
        markRunDirty();
    }

    protected final void requestPublish() {
        schedulePublish();
    }

    @Override
    public void setLevel(Level level) {
        super.setLevel(level);
        applyPendingBlockRotation();
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
        BlockState migrated = state.setValue(
                StackBlock.HORIZONTAL_FACING,
                QuarterTurns.toDirection(pendingBlockRotation));
        if (state == migrated || level.setBlock(getBlockPos(), migrated, Block.UPDATE_ALL)) {
            pendingBlockRotation = null;
            setChanged();
        }
    }

    final boolean publishIfPending(ServerLevel currentLevel) {
        if (!publishPending) {
            return false;
        }
        publishPending = false;
        syncToClients();

        int newLight = ItemOps.calculateLightLevelFromItems(items);
        BlockState state = getBlockState();
        if (state.getValue(StackBlock.LIGHT_LEVEL) != newLight) {
            currentLevel.setBlock(getBlockPos(),
                    state.setValue(StackBlock.LIGHT_LEVEL, newLight), Block.UPDATE_ALL);
        }
        return true;
    }

    @Override
    protected final void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
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
            items.deserializeNBT(registries, tag.getCompound(TAG_ITEMS), getBlockPos());
        }
        loadStackData(tag, registries);
        refreshComparatorContribution();
    }

    @Override
    protected final void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        StackDataMigration.stampVersion(tag);
        tag.put(TAG_ITEMS, items.serializeNBT(registries));
        saveStackData(tag, registries);
    }

    @Override
    public final ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public final CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        saveAdditional(tag, registries);
        StackItemStorage.stripSetAside(tag.getCompound(TAG_ITEMS));
        stripServerOnlyUpdateData(tag);
        return tag;
    }
}
