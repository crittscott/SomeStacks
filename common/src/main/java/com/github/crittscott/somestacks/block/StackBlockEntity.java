package com.github.crittscott.somestacks.block;

import com.github.crittscott.somestacks.SomeStacksCommon;
import com.github.crittscott.somestacks.util.ItemOps;
import com.github.crittscott.somestacks.util.QuarterTurns;
import com.github.crittscott.somestacks.util.StackItemStorage;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/** Local storage, persistence, synchronization, lighting, and batching shared by every stack. */
public abstract class StackBlockEntity extends BlockEntity {
    public static final String TAG_ITEMS = StackItemStorage.TAG_ITEMS;
    public static final String TAG_DATA_VERSION = "DataVersion";

    private static boolean reportedClientUpdateRejection;

    protected final StackItemStorage items;
    @Nullable
    private StackDataMigration.LoadResult pendingLoadResult;
    private boolean pendingMigrationSave;
    /** Client-only prepared presentation, discarded whenever server contents replace this entity's. */
    @Nullable
    private Object renderCache;
    /** A released save's block-entity layout rotation, converted to native facing once attached. */
    @Nullable
    private Integer pendingLegacyRotation;
    private int batchDepth;
    private boolean batchTouched;
    private boolean publishPending;
    private int publishedSignal = -1;
    private double comparatorContribution;
    private final double[] slotContributions;
    private boolean comparatorDirty;
    private StackRunItemAccess cachedRun;
    private long cachedRunTick = Long.MIN_VALUE;

    /** Creates fixed local storage whose mutations refresh fill, notify the subtype, and batch publication. */
    protected StackBlockEntity(
            BlockEntityType<?> type, BlockPos pos, BlockState state, int slots) {
        super(type, pos, state);
        slotContributions = new double[slots];
        items = new StackItemStorage(slots) {
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
            protected int getSlotLimit(int slot) {
                return localSlotLimit();
            }

            @Override
            protected boolean isItemValid(int slot, @Nonnull ItemStack stack) {
                return isStoredItemValid(stack);
            }
        };
    }

    /**
     * Admission for new local/automation inserts, including current namespace and type policy.
     * Existing saved or internally relocated items bypass this check; no slot is selected here.
     */
    public abstract boolean isStoredItemValid(ItemStack stack);

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

    /** Shares one resolved run with all current members for this server tick. */
    final void cacheRun(StackRunItemAccess run, long tick) {
        cachedRun = run;
        cachedRunTick = tick;
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

    /** Full-block contents by default; positional entities supply their cached occupied shape. */
    public VoxelShape getCachedShape() {
        return Shapes.block();
    }

    /** Local slot capacity before the item's own stack limit, or one for positional display types. */
    public int localSlotLimit() {
        return StackItemStorage.STORAGE_SLOT_LIMIT;
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

    /** Updates only the changed slot; summing cached fractions on demand avoids cumulative rounding drift. */
    private void refreshSlotContribution(int slot) {
        ItemStack stack = items.getStackInSlot(slot);
        slotContributions[slot] = stack.isEmpty() ? 0.0 : localSlotLimit() == 1 ? 1.0
                : Math.min(1.0, (double) stack.getCount() / Math.min(localSlotLimit(), stack.getMaxStackSize()));
        comparatorDirty = true;
    }

    private void refreshComparatorContribution() {
        for (int slot = 0; slot < items.getSlots(); slot++) {
            refreshSlotContribution(slot);
        }
        comparatorContribution();
    }

    /**
     * Invalidates subtype-derived local state after a mutation to {@code 0 <= slot < getItems().getSlots()}.
     * Runs on either side after dirty/fill updates, even inside a batch, before deferred publication.
     * Disk and client snapshot loads bypass this hook and must refresh subtype state in loadStackData.
     */
    protected void onLocalContentsChanged(int slot) {
    }

    /**
     * Loads subtype metadata and refreshes derived state after inventory replacement. Disk data is
     * already migrated; client data is already validated. Runs on either side without publication.
     */
    protected void loadStackData(ValueInput input) {
    }

    /** Checks type-specific update data before inventory or metadata is changed. */
    protected boolean isValidClientStackData(CompoundTag tag) {
        return true;
    }

    /**
     * Adds current subtype metadata after version and inventory serialization. Used for both disk
     * saves and update tags; remove private fields in stripServerOnlyUpdateData before transmission.
     */
    protected void saveStackData(ValueOutput output) {
    }

    /** Removes server-only state before a saved tag is sent as a client update. */
    protected void stripServerOnlyUpdateData(CompoundTag tag) {
    }

    /** Whether every live local slot is empty; retained disk entries do not count as live contents. */
    public final boolean isEmpty() {
        return ItemOps.isHandlerEmpty(items);
    }

    /** Mutable local storage, indexed from zero to its slot count exclusively, not the whole run. */
    public final StackItemStorage getItems() {
        return items;
    }

    /** The outer block's layout rotation, expressed in the grid's quarter-turn coordinates. */
    protected final int blockRotation() {
        BlockState state = getBlockState();
        return state.hasProperty(StackBlock.HORIZONTAL_FACING)
                ? QuarterTurns.fromDirection(state.getValue(StackBlock.HORIZONTAL_FACING))
                : 0;
    }

    /** Immediately notifies block updates when attached; server callers send the local render state. */
    public final void syncToClients() {
        if (level != null) {
            BlockState state = getBlockState();
            level.sendBlockUpdated(getBlockPos(), state, state, Block.UPDATE_ALL);
        }
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

    /** Ends a batch, scheduling one server publication pass when the outermost touched batch ends. */
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

    /** Whether a mutation batch is active, including nested batches; derived-state hooks still run. */
    protected final boolean isBatching() {
        return batchDepth > 0;
    }

    /** Marks local publication pending and coalesces a run tick; unattached/client entities do nothing. */
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
        if (pendingMigrationSave && level instanceof ServerLevel) {
            setChanged();
            pendingMigrationSave = false;
        }
        applyLegacyRotation();
        reportSavedItems();
    }

    /** Drops contents while this entity is still available; the block repairs neighboring runs afterward. */
    @Override
    public final void preRemoveSideEffects(BlockPos pos, BlockState oldState) {
        super.preRemoveSideEffects(pos, oldState);
        if (level instanceof ServerLevel serverLevel) {
            dropContentsOnRemoval(serverLevel, pos);
        }
    }

    /** Bar overrides this to collect its contents and unsupported bars in one drop batch. */
    protected void dropContentsOnRemoval(ServerLevel serverLevel, BlockPos pos) {
        ItemOps.dropAllItems(items, serverLevel, pos);
    }

    /** Reports disk recovery only after server attachment supplies a dimension. */
    private void reportSavedItems() {
        if (pendingLoadResult == null || level == null) return;
        if (level instanceof ServerLevel serverLevel) {
            StackDataMigration.reportSavedItems(serverLevel, getBlockPos(), pendingLoadResult);
        }
        pendingLoadResult = null;
    }

    /** Converts a migrated layout rotation into native facing once the block belongs to a server level. */
    private void applyLegacyRotation() {
        if (pendingLegacyRotation == null || level == null || level.isClientSide) {
            return;
        }
        BlockState state = getBlockState();
        if (!state.hasProperty(StackBlock.HORIZONTAL_FACING)) {
            pendingLegacyRotation = null;
            return;
        }
        BlockState migrated = state.setValue(
                StackBlock.HORIZONTAL_FACING,
                QuarterTurns.toDirection(pendingLegacyRotation));
        if (state == migrated || level.getBlockState(getBlockPos()) == migrated
                || level.setBlock(getBlockPos(), migrated, Block.UPDATE_ALL)) {
            setBlockState(migrated);
            pendingLegacyRotation = null;
            setChanged();
        }
    }

    /** Publishes pending contents and light on the owning server level; returns whether it did work. */
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
    protected final void loadAdditional(ValueInput input) {
        CompoundTag tag = StackDataMigration.readTag(input);
        HolderLookup.Provider registries = input.lookup();
        if (level != null && level.isClientSide) {
            if (loadClientUpdate(tag, registries)) {
                super.loadAdditional(input);
            }
            return;
        }
        super.loadAdditional(input);
        pendingLoadResult = null;
        StackDataMigration.LoadOutcome loaded = StackDataMigration.load(
                tag, items, registries, this instanceof StorageStackBE);
        pendingMigrationSave = loaded.changed();
        Integer legacyBlockRotation = StackDataMigration.takeLegacyBlockRotation(tag);
        if (legacyBlockRotation != null) {
            pendingLegacyRotation = legacyBlockRotation;
            applyLegacyRotation();
            pendingMigrationSave = true;
        }
        if (pendingMigrationSave && level instanceof ServerLevel) {
            setChanged();
            pendingMigrationSave = false;
        }
        pendingLoadResult = loaded.result();
        reportSavedItems();
        loadStackData(input);
        refreshComparatorContribution();
    }

    /** Validates native ingress before inherited loading can change components or inventory. */
    public final boolean isValidClientUpdate(ValueInput input) {
        CompoundTag tag = StackDataMigration.readTag(input);
        String reason = invalidClientMetadata(tag);
        if (reason != null) return rejectClientUpdate(reason);
        return items.isValidClientUpdate(input.lookup(), tag.getCompound(TAG_ITEMS).orElseThrow())
                || rejectClientUpdate("invalid inventory");
    }

    @Nullable
    private String invalidClientMetadata(CompoundTag tag) {
        if (!(tag.get(TAG_DATA_VERSION) instanceof IntTag version)
                || version.intValue() != SharedConstants.getCurrentVersion().dataVersion().version()) {
            return "missing or mismatched data version";
        }
        if (!(tag.get(TAG_ITEMS) instanceof CompoundTag)) return "missing or malformed inventory";
        if (tag.contains(StackItemStorage.TAG_SET_ASIDE) || tag.contains(StorageStackBE.TAG_PERMANENT)
                || tag.contains(StackDataMigration.LEGACY_TAG_ROTATION)) {
            return "server-only field";
        }
        return isValidClientStackData(tag) ? null : "invalid stack metadata";
    }

    private boolean rejectClientUpdate(String reason) {
        if (level != null && level.isClientSide && !reportedClientUpdateRejection) {
            reportedClientUpdateRejection = true;
            SomeStacksCommon.LOGGER.warn("Rejected Some Stacks client update in {} at {} ({}): {}",
                    level.dimension().location(), getBlockPos().toShortString(), getBlockState().getBlock(), reason);
        }
        return false;
    }

    /** Starts a new connection's diagnostic budget without retaining block positions. */
    public static void resetClientUpdateDiagnostic() { reportedClientUpdateRejection = false; }

    /** Rejects malformed wire data atomically, without migration or recovery retention. */
    public final boolean loadClientUpdate(CompoundTag tag, HolderLookup.Provider registries) {
        String reason = invalidClientMetadata(tag);
        if (reason != null) return rejectClientUpdate(reason);
        if (!items.deserializeClientUpdate(registries, tag.getCompound(TAG_ITEMS).orElseThrow())) {
            return rejectClientUpdate("invalid inventory");
        }
        pendingLegacyRotation = null;
        loadStackData(TagValueInput.create(ProblemReporter.DISCARDING, registries, tag));
        refreshComparatorContribution();
        renderCache = null;
        return true;
    }

    /** The renderer's prepared presentation for the current contents, or null before preparation. */
    @Nullable
    public final Object renderCache() {
        return renderCache;
    }

    public final void setRenderCache(@Nullable Object cache) {
        renderCache = cache;
    }

    @Override
    protected final void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        output.putInt(TAG_DATA_VERSION, SharedConstants.getCurrentVersion().dataVersion().version());
        try {
            output.store(TAG_ITEMS, StackItemStorage.codec(items.getSlots()), items);
        } catch (RuntimeException failure) {
            String dimension = level == null ? "unattached" : level.dimension().location().toString();
            throw new IllegalStateException("Cannot save Some Stacks inventory in " + dimension
                    + " at " + getBlockPos(), failure);
        }
        saveStackData(output);
    }

    @Override
    public final ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public final CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = saveWithoutMetadata(registries);
        tag.remove("Rotation");
        tag.getCompound(TAG_ITEMS).ifPresent(StackItemStorage::stripSetAside);
        stripServerOnlyUpdateData(tag);
        return tag;
    }
}
