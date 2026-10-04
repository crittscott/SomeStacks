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

import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Fabric Transfer API view over a complete Storage pile, Singles column, or Bar column.
 * Slot changes are staged in the Fabric transaction and applied through the shared run mutations
 * only after its outer commit, so an aborted transaction never edits the world. Singles and Bar
 * accept one extraction position per transaction because each extraction remaps later positions.
 */
final class FabricRunItemStorage extends SnapshotParticipant<FabricRunItemStorage.State>
        implements SlottedStorage<ItemVariant> {
    private final StackBlockEntity blockEntity;
    private final Map<Integer, RunSlot> slotViews = new HashMap<>();
    /** First live value observed for each touched slot, in the order its final diff must replay. */
    private LinkedHashMap<Integer, ItemStack> originals = new LinkedHashMap<>();
    /** Latest transaction-visible value for each touched slot. */
    private Map<Integer, ItemStack> staged = new HashMap<>();
    /** The one Singles or Bar position whose structural extraction this transaction may stage. */
    private int structuralExtractionSlot = -1;

    FabricRunItemStorage(StackBlockEntity blockEntity) {
        this.blockEntity = blockEntity;
    }

    @Override
    public int getSlotCount() {
        StackRunItemAccess run = blockEntity.itemRun();
        return run != null ? run.advertisedSlots() : blockEntity.getItems().getSlots();
    }

    @Override
    public SingleSlotStorage<ItemVariant> getSlot(int slot) {
        if (slot < 0 || slot >= getSlotCount()) {
            throw new IndexOutOfBoundsException("Slot " + slot + " is outside this run");
        }
        return slotViews.computeIfAbsent(slot, RunSlot::new);
    }

    @Override
    public Iterator<StorageView<ItemVariant>> iterator() {
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
        return StorageUtil.insertStacking(getSlots(), resource, maxAmount, transaction);
    }

    @Override
    public long extract(ItemVariant resource, long maxAmount, TransactionContext transaction) {
        StoragePreconditions.notBlankNotNegative(resource, maxAmount);
        long extracted = 0;
        int slotCount = getSlotCount();
        for (int slot = 0; slot < slotCount && extracted < maxAmount; slot++) {
            extracted += getSlot(slot).extract(resource, maxAmount - extracted, transaction);
        }
        return extracted;
    }

    private ItemStack currentStack(int slot) {
        ItemStack pending = staged.get(slot);
        return pending != null ? pending : liveStack(slot);
    }

    private ItemStack liveStack(int slot) {
        StackRunItemAccess run = blockEntity.itemRun();
        if (run != null) {
            return run.getSlot(slot);
        }
        return slot >= 0 && slot < blockEntity.getItems().getSlots()
                ? blockEntity.getItems().getStackInSlot(slot)
                : ItemStack.EMPTY;
    }

    private boolean canInsert(int slot, ItemStack stack) {
        StackRunItemAccess run = blockEntity.itemRun();
        return run != null && run.insertAt(slot, stack, true) > 0;
    }

    private boolean isValid(ItemStack stack) {
        return blockEntity.acceptsAutomation(stack);
    }

    private int capacity(ItemVariant resource) {
        return Math.min(blockEntity.automationSlotLimit(), resource.toStack().getMaxStackSize());
    }

    private boolean isStructural() {
        return blockEntity.automationSlotLimit() == 1;
    }

    /** Records a slot's original live value once, then replaces its transaction-visible value. */
    private void stage(int slot, ItemStack stack, TransactionContext transaction) {
        updateSnapshots(transaction);
        originals.computeIfAbsent(slot, ignored -> liveStack(slot).copy());
        staged.put(slot, stack);
    }

    private void commitInsert(int slot, ItemStack stack) {
        StackRunItemAccess run = blockEntity.itemRun();
        if (run != null) {
            run.insertAt(slot, stack, false);
        }
    }

    private void commitExtract(int slot, int amount) {
        StackRunItemAccess run = blockEntity.itemRun();
        if (run != null) {
            run.extract(slot, amount, false);
        }
    }

    @Override
    protected State createSnapshot() {
        return new State(copyStacks(originals), copyStacks(staged), structuralExtractionSlot);
    }

    @Override
    protected void readSnapshot(State snapshot) {
        originals = copyStacks(snapshot.originals());
        staged = copyStacks(snapshot.staged());
        structuralExtractionSlot = snapshot.structuralExtractionSlot();
    }

    /**
     * Replays each touched slot's net difference through shared run mutations after the outer
     * transaction commits. Insertion order matters for positional and structural mutations.
     */
    @Override
    protected void onFinalCommit() {
        LinkedHashMap<Integer, ItemStack> committedOriginals = originals;
        Map<Integer, ItemStack> committedStacks = staged;
        originals = new LinkedHashMap<>();
        staged = new HashMap<>();
        structuralExtractionSlot = -1;

        if (!RunEdit.begin()) {
            return;
        }
        try {
            for (Map.Entry<Integer, ItemStack> entry : committedOriginals.entrySet()) {
                int slot = entry.getKey();
                ItemStack original = entry.getValue();
                ItemStack result = committedStacks.get(slot);
                if (ItemStack.isSameItemSameComponents(original, result)) {
                    int difference = result.getCount() - original.getCount();
                    if (difference > 0) {
                        ItemStack inserted = result.copy();
                        inserted.setCount(difference);
                        commitInsert(slot, inserted);
                    } else if (difference < 0) {
                        commitExtract(slot, -difference);
                    }
                } else {
                    if (!original.isEmpty()) {
                        commitExtract(slot, original.getCount());
                    }
                    if (!result.isEmpty()) {
                        commitInsert(slot, result);
                    }
                }
            }
        } finally {
            RunEdit.end();
        }
    }

    private static LinkedHashMap<Integer, ItemStack> copyStacks(Map<Integer, ItemStack> source) {
        LinkedHashMap<Integer, ItemStack> copy = new LinkedHashMap<>();
        source.forEach((slot, stack) -> copy.put(slot, stack.copy()));
        return copy;
    }

    /** Transaction rollback state, including the structural-position restriction. */
    record State(
            LinkedHashMap<Integer, ItemStack> originals,
            LinkedHashMap<Integer, ItemStack> staged,
            int structuralExtractionSlot) {
    }

    private final class RunSlot implements SingleSlotStorage<ItemVariant> {
        private final int slot;

        private RunSlot(int slot) {
            this.slot = slot;
        }

        @Override
        public long insert(ItemVariant resource, long maxAmount, TransactionContext transaction) {
            StoragePreconditions.notBlankNotNegative(resource, maxAmount);
            if (maxAmount == 0 || RunEdit.isInProgress() || isStructural()
                    && structuralExtractionSlot >= 0 && structuralExtractionSlot != slot) {
                return 0;
            }

            ItemStack current = currentStack(slot);
            if (!current.isEmpty() && !resource.matches(current)) {
                return 0;
            }

            int amount = (int) Math.min(maxAmount, capacity(resource) - current.getCount());
            if (amount <= 0) {
                return 0;
            }
            ItemStack offered = resource.toStack(amount);
            if (!isValid(offered) || !canInsert(slot, offered)) {
                return 0;
            }

            ItemStack result = current.isEmpty() ? offered : current.copy();
            if (!current.isEmpty()) {
                result.grow(amount);
            }
            stage(slot, result, transaction);
            return amount;
        }

        @Override
        public long extract(ItemVariant resource, long maxAmount, TransactionContext transaction) {
            StoragePreconditions.notBlankNotNegative(resource, maxAmount);
            if (maxAmount == 0 || RunEdit.isInProgress() || isStructural()
                    && structuralExtractionSlot >= 0 && structuralExtractionSlot != slot) {
                return 0;
            }

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
                structuralExtractionSlot = slot;
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
                    ? blockEntity.automationSlotLimit()
                    : capacity(resource);
        }
    }
}
