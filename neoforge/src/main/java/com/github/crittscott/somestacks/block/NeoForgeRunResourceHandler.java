package com.github.crittscott.somestacks.block;

import com.github.crittscott.somestacks.NeoForgeRegistry;
import com.github.crittscott.somestacks.server.NeoForgeGrowthPlacement;
import com.github.crittscott.somestacks.util.StackPlacement;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.TransferPreconditions;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.transaction.SnapshotJournal;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;

/**
 * Whole-run NeoForge transfer view. Only the ledger changes during a transaction; nested aborts
 * restore it and the root commit applies net slot changes through the common movement rules.
 * Insertion checks the staged contents, so one transaction can build on its own pending changes.
 * Positional runs permit one structural extraction position, without other pending positions or
 * later insertion, because gravity and backfill remap the slots at commit.
 */
public final class NeoForgeRunResourceHandler extends SnapshotJournal<NeoForgeRunResourceHandler.State>
        implements ResourceHandler<ItemResource> {
    private final StackBlockEntity blockEntity;
    private final RunTransferLedger changes = new RunTransferLedger();
    private NeoForgeGrowthPlacement growth;

    public NeoForgeRunResourceHandler(StackBlockEntity blockEntity) {
        this.blockEntity = blockEntity;
    }

    private StackRun<?> run() {
        return !blockEntity.isRemoved() && blockEntity.itemRun() instanceof StackRun<?> run ? run : null;
    }

    /** Sided lookups and different blocks in a run all address one native journal. */
    private NeoForgeRunResourceHandler ledger() {
        StackRun<?> run = run();
        if (run == null) return this;
        StackBlockEntity base = run.baseBlockEntity();
        return base == blockEntity ? this : base.getData(NeoForgeRegistry.RUN_RESOURCES);
    }

    @Override
    public int size() {
        StackRun<?> run = run();
        return run == null ? 0 : run.advertisedSlots();
    }

    private ItemStack current(int slot) {
        NeoForgeRunResourceHandler ledger = ledger();
        if (ledger != this) return ledger.current(slot);
        if (slot < 0 || slot >= size()) return ItemStack.EMPTY;
        return changes.current(slot, run());
    }

    @Override
    public ItemResource getResource(int index) {
        return ItemResource.of(current(index));
    }

    @Override
    public long getAmountAsLong(int index) {
        return current(index).getCount();
    }

    @Override
    public boolean isValid(int index, ItemResource resource) {
        return index >= 0 && index < size() && !resource.isEmpty()
                && blockEntity.isStoredItemValid(resource.toStack());
    }

    @Override
    public long getCapacityAsLong(int index, ItemResource resource) {
        if (index < 0 || index >= size()) return 0;
        if (resource.isEmpty()) return blockEntity.localSlotLimit();
        return isValid(index, resource)
                ? Math.min(blockEntity.localSlotLimit(), resource.getMaxStackSize()) : 0;
    }

    private boolean structural() {
        return blockEntity.localSlotLimit() == 1;
    }

    @Override
    public int insert(int index, ItemResource resource, int amount, TransactionContext transaction) {
        TransferPreconditions.checkNonEmptyNonNegative(resource, amount);
        NeoForgeRunResourceHandler ledger = ledger();
        if (ledger != this) return ledger.insert(index, resource, amount, transaction);
        if (amount == 0 || RunEdit.isInProgress() || !isValid(index, resource)
                || !changes.allowsInsertion()) return 0;
        ItemStack current = current(index);
        if (!current.isEmpty() && !resource.matches(current)) return 0;
        int inserted = Math.min(amount, getCapacityAsInt(index, resource) - current.getCount());
        if (inserted <= 0) return 0;

        StackRun<?> run = run();
        inserted = Math.min(inserted, run.acceptsAt(index, resource.toStack(inserted), this::current));
        if (inserted <= 0) return 0;
        if (index >= run.totalSlots()) {
            ServerLevel level = (ServerLevel) blockEntity.getLevel();
            BlockPos pos = run.basePos().above(run.height());
            if (growth == null) {
                if (!RunEdit.begin()) return 0;
                NeoForgeGrowthPlacement prepared;
                try {
                    prepared = NeoForgeGrowthPlacement.prepare(level, pos,
                            StackPlacement.stateFor(blockEntity.getBlockState().getBlock(), level, pos));
                } finally {
                    RunEdit.end();
                }
                if (prepared == null) return 0;
                updateSnapshots(transaction);
                growth = prepared;
            }
        }
        ItemStack result = resource.toStack(current.getCount() + inserted);
        stage(index, result, transaction);
        return inserted;
    }

    /** Compatible partial slots precede empty slots, including the advertised headroom. */
    @Override
    public int insert(ItemResource resource, int amount, TransactionContext transaction) {
        TransferPreconditions.checkNonEmptyNonNegative(resource, amount);
        NeoForgeRunResourceHandler base = ledger();
        if (base != this) return base.insert(resource, amount, transaction);
        StackRun<?> run = run();
        if (run == null) return 0;
        try (var query = run.growthQuery()) {
            int inserted = 0;
            int size = size();
            for (int pass = 0; pass < 2 && inserted < amount; pass++) {
                for (int slot = 0; slot < size && inserted < amount; slot++) {
                    if (current(slot).isEmpty() == (pass == 1)) {
                        inserted += insert(slot, resource, amount - inserted, transaction);
                    }
                }
            }
            return inserted;
        }
    }

    @Override
    public int extract(int index, ItemResource resource, int amount, TransactionContext transaction) {
        TransferPreconditions.checkNonEmptyNonNegative(resource, amount);
        NeoForgeRunResourceHandler ledger = ledger();
        if (ledger != this) return ledger.extract(index, resource, amount, transaction);
        if (amount == 0 || RunEdit.isInProgress() || index < 0 || index >= size()) return 0;
        if (structural() && !changes.mayExtract(index)) return 0;
        ItemStack current = current(index);
        if (current.isEmpty() || !resource.matches(current)) return 0;
        int extracted = Math.min(amount, current.getCount());
        ItemStack result = current.copy();
        result.shrink(extracted);
        stage(index, result, transaction);
        if (structural()) changes.noteStructuralExtraction(index);
        return extracted;
    }

    private void stage(int slot, ItemStack result, TransactionContext transaction) {
        updateSnapshots(transaction);
        changes.stage(slot, result, run());
    }

    @Override
    protected State createSnapshot() { return new State(changes.snapshot(), growth); }

    @Override
    protected void revertToSnapshot(State snapshot) {
        changes.restore(snapshot.ledger());
        growth = snapshot.growth();
    }

    @Override
    protected void onRootCommit(State originalState) {
        NeoForgeGrowthPlacement placement = growth;
        growth = null;
        if (!RunEdit.begin()) throw new IllegalStateException("Reentrant transfer root commit");
        try {
            boolean grows = changes.growsBeyond(run().totalSlots());
            var snapshots = grows ? placement.install() : null;
            if (grows) blockEntity.invalidateRunCache();
            changes.replay(this::run, blockEntity.getBlockPos());
            if (grows) placement.publish(snapshots);
        } finally {
            RunEdit.end();
        }
    }

    /** Native checkpoint also retains NeoForge-only placement preparation. */
    record State(RunTransferLedger.State ledger, NeoForgeGrowthPlacement growth) {}
}
