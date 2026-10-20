package com.github.crittscott.somestacks.block;

import net.fabricmc.fabric.api.transfer.v1.item.ItemVariant;
import net.fabricmc.fabric.api.transfer.v1.storage.SlottedStorage;
import net.fabricmc.fabric.api.transfer.v1.storage.StoragePreconditions;
import net.fabricmc.fabric.api.transfer.v1.storage.StorageUtil;
import net.fabricmc.fabric.api.transfer.v1.storage.StorageView;
import net.fabricmc.fabric.api.transfer.v1.storage.base.SingleSlotStorage;
import net.fabricmc.fabric.api.transfer.v1.transaction.TransactionContext;
import net.fabricmc.fabric.api.transfer.v1.transaction.base.SnapshotParticipant;
import net.minecraft.world.item.ItemStack;


import java.util.ArrayList;
import java.util.List;
import java.util.Iterator;

/**
 * Fabric Transfer API view over a complete Storage pile, Singles column, or Bar column.
 * Slot changes are staged in the Fabric transaction and applied through the shared run mutations
 * only after its outer commit, so an aborted transaction never edits the world. Insertion is judged
 * against the staged contents. Singles and Bar accept one extraction position per transaction,
 * without other pending positions or later insertion, because gravity and backfill remap the
 * positions at commit.
 */
public final class FabricRunItemStorage extends SnapshotParticipant<RunTransferLedger.State>
        implements SlottedStorage<ItemVariant> {
    private final StackBlockEntity blockEntity;
    private final List<RunSlot> slotViews = new ArrayList<>();
    private final RunTransferLedger changes = new RunTransferLedger();

    public FabricRunItemStorage(StackBlockEntity blockEntity) {
        this.blockEntity = blockEntity;
    }

    @Override
    public int getSlotCount() {
        StackRunItemAccess run = blockEntity.isRemoved() ? null : blockEntity.itemRun();
        return run != null ? run.advertisedSlots() : 0;
    }

    @Override
    public SingleSlotStorage<ItemVariant> getSlot(int slot) {
        if (slot < 0 || slot >= getSlotCount()) {
            throw new IndexOutOfBoundsException("Slot " + slot + " is outside this run");
        }
        while (slotViews.size() <= slot) {
            slotViews.add(null);
        }
        RunSlot view = slotViews.get(slot);
        if (view == null) {
            view = new RunSlot(slot);
            slotViews.set(slot, view);
        }
        return view;
    }

    @Override
    public Iterator<StorageView<ItemVariant>> iterator() {
        FabricRunItemStorage ledger = ledger();
        if (ledger != this) return ledger.iterator();
        int slotCount = getSlotCount();
        return new Iterator<>() {
            private int slot;

            @Override
            public boolean hasNext() {
                return slot < slotCount;
            }

            @Override
            public StorageView<ItemVariant> next() {
                return getSlot(slot++);
            }
        };
    }

    @Override
    public long insert(ItemVariant resource, long maxAmount, TransactionContext transaction) {
        StoragePreconditions.notBlankNotNegative(resource, maxAmount);
        FabricRunItemStorage ledger = ledger();
        if (ledger != this) return ledger.insert(resource, maxAmount, transaction);
        if (!(blockEntity.itemRun() instanceof StackRun<?> run)) return 0;
        try (var query = run.growthQuery()) {
            return StorageUtil.insertStacking(getSlots(), resource, maxAmount, transaction);
        }
    }

    @Override
    public long extract(ItemVariant resource, long maxAmount, TransactionContext transaction) {
        StoragePreconditions.notBlankNotNegative(resource, maxAmount);
        FabricRunItemStorage ledger = ledger();
        if (ledger != this) return ledger.extract(resource, maxAmount, transaction);
        long extracted = 0;
        int slotCount = getSlotCount();
        for (int slot = 0; slot < slotCount && extracted < maxAmount; slot++) {
            extracted += getSlot(slot).extract(resource, maxAmount - extracted, transaction);
        }
        return extracted;
    }

    /** All adapters addressing one run share its bottom block's transaction ledger. */
    private FabricRunItemStorage ledger() {
        if (blockEntity.isRemoved()) return this;
        StackRunItemAccess access = blockEntity.itemRun();
        if (access instanceof StackRun<?> run) {
            StackBlockEntity base = run.baseBlockEntity();
            if (base == blockEntity) return this;
            return FabricItemStorage.of(base);
        }
        return this;
    }

    private ItemStack currentStack(int slot) {
        FabricRunItemStorage ledger = ledger();
        if (ledger != this) {
            return ledger.currentStack(slot);
        }
        if (slot < 0 || slot >= getSlotCount()) return ItemStack.EMPTY;
        return changes.current(slot, blockEntity.itemRun());
    }

    /** How much of {@code stack} the slot accepts given the contents this transaction has staged. */
    private int acceptedAt(int slot, ItemStack stack) {
        if (blockEntity.isRemoved()) return 0;
        StackRunItemAccess run = blockEntity.itemRun();
        return run != null ? run.acceptsAt(slot, stack, this::currentStack) : 0;
    }

    private boolean isValid(ItemStack stack) {
        return blockEntity.isStoredItemValid(stack);
    }

    private int capacity(ItemVariant resource) {
        return Math.min(blockEntity.localSlotLimit(), resource.toStack().getMaxStackSize());
    }

    private boolean isStructural() {
        return blockEntity.localSlotLimit() == 1;
    }

    /** Records a slot's original live value once, then replaces its transaction-visible value. */
    private void stage(int slot, ItemStack stack, TransactionContext transaction) {
        updateSnapshots(transaction);
        changes.stage(slot, stack, run());
    }


    @Override
    protected RunTransferLedger.State createSnapshot() { return changes.snapshot(); }

    @Override
    protected void readSnapshot(RunTransferLedger.State snapshot) { changes.restore(snapshot); }

    @Override
    protected void onFinalCommit() {
        if (!RunEdit.begin()) throw new IllegalStateException("Reentrant transfer commit");
        try {
            changes.replay(this::run, blockEntity.getBlockPos());
        } finally {
            RunEdit.end();
        }
    }

    private StackRunItemAccess run() { return blockEntity.itemRun(); }

    private final class RunSlot implements SingleSlotStorage<ItemVariant> {
        private final int slot;

        private RunSlot(int slot) {
            this.slot = slot;
        }

        @Override
        public long insert(ItemVariant resource, long maxAmount, TransactionContext transaction) {
            FabricRunItemStorage ledger = ledger();
            if (ledger != FabricRunItemStorage.this) {
                return slot < ledger.getSlotCount()
                        ? ledger.getSlot(slot).insert(resource, maxAmount, transaction) : 0;
            }
            StoragePreconditions.notBlankNotNegative(resource, maxAmount);
            if (maxAmount == 0 || RunEdit.isInProgress() || !changes.allowsInsertion()) {
                return 0;
            }

            ItemStack current = currentStack(slot);
            if (!current.isEmpty() && !resource.matches(current)) {
                return 0;
            }

            int amount = (int) Math.min(maxAmount, capacity(resource) - current.getCount());
            if (amount <= 0 || !isValid(resource.toStack(amount))) {
                return 0;
            }
            amount = Math.min(amount, acceptedAt(slot, resource.toStack(amount)));
            if (amount <= 0) {
                return 0;
            }
            ItemStack offered = resource.toStack(amount);

            ItemStack result = current.isEmpty() ? offered : current.copy();
            if (!current.isEmpty()) {
                result.grow(amount);
            }
            stage(slot, result, transaction);
            return amount;
        }

        @Override
        public long extract(ItemVariant resource, long maxAmount, TransactionContext transaction) {
            FabricRunItemStorage ledger = ledger();
            if (ledger != FabricRunItemStorage.this) {
                return slot < ledger.getSlotCount()
                        ? ledger.getSlot(slot).extract(resource, maxAmount, transaction) : 0;
            }
            StoragePreconditions.notBlankNotNegative(resource, maxAmount);
            if (maxAmount == 0 || RunEdit.isInProgress()
                    || isStructural() && !changes.mayExtract(slot)) return 0;

            ItemStack current = currentStack(slot);
            if (current.isEmpty() || !resource.matches(current)) {
                return 0;
            }

            int amount = (int) Math.min(maxAmount, current.getCount());
            if (amount <= 0) {
                return 0;
            }

            ItemStack result = current.copy();
            result.shrink(amount);
            stage(slot, result, transaction);
            if (isStructural()) {
                changes.noteStructuralExtraction(slot);
            }
            return amount;
        }

        @Override
        public boolean isResourceBlank() {
            return currentStack(slot).isEmpty();
        }

        @Override
        public ItemVariant getResource() {
            return ItemVariant.of(currentStack(slot));
        }

        @Override
        public long getAmount() {
            return currentStack(slot).getCount();
        }

        @Override
        public long getCapacity() {
            ItemVariant resource = getResource();
            return resource.isBlank() && !isStructural()
                    ? blockEntity.localSlotLimit()
                    : capacity(resource);
        }
    }
}
