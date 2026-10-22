package com.github.crittscott.somestacks.block;

import net.fabricmc.fabric.api.transfer.v1.item.ItemVariant;
import net.fabricmc.fabric.api.transfer.v1.storage.SlottedStorage;
import net.fabricmc.fabric.api.transfer.v1.storage.StoragePreconditions;
import net.fabricmc.fabric.api.transfer.v1.storage.StorageView;
import net.fabricmc.fabric.api.transfer.v1.storage.base.SingleSlotStorage;
import net.fabricmc.fabric.api.transfer.v1.transaction.TransactionContext;
import net.fabricmc.fabric.api.transfer.v1.transaction.base.SnapshotParticipant;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Fabric Transfer API view over a complete Storage pile, Singles column, or Bar column.
 * Slot changes are staged in the Fabric transaction and applied through the shared run mutations
 * only after its outer commit, so an aborted transaction never edits the world. Singles and Bar
 * accept one extraction position per transaction because each extraction remaps later positions.
 */
public final class FabricRunItemStorage extends SnapshotParticipant<FabricRunItemStorage.State>
        implements SlottedStorage<ItemVariant> {
    private final StackBlockEntity blockEntity;
    private final List<RunSlot> slotViews = new ArrayList<>();
    /** First live value observed for each touched slot, in the order its final diff must replay. */
    private LinkedHashMap<Integer, ItemStack> originals = new LinkedHashMap<>();
    /** Latest transaction-visible value for each touched slot. */
    private Map<Integer, ItemStack> staged = new HashMap<>();
    /** The one Singles or Bar position whose structural extraction this transaction may stage. */
    private int structuralExtractionSlot = -1;

    public FabricRunItemStorage(StackBlockEntity blockEntity) {
        this.blockEntity = blockEntity;
    }

    @Override
    public int getSlotCount() {
        return blockEntity.advertisedItemSlots();
    }

    @Override
    public SingleSlotStorage<ItemVariant> getSlot(int slot) {
        if (slot < 0 || slot >= getSlotCount()) {
            throw new IndexOutOfBoundsException("Slot " + slot + " is outside this run");
        }
        while (slotViews.size() <= slot) slotViews.add(null);
        RunSlot view = slotViews.get(slot);
        if (view == null) {
            view = new RunSlot(slot);
            slotViews.set(slot, view);
        }
        return view;
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
        if (maxAmount == 0 || RunEdit.isInProgress()) return 0;
        Access access = access();
        if (access.run == null) return 0;
        FabricRunItemStorage ledger = access.ledger;
        StackRunItemAccess.InsertionTest insertion = access.run.insertionTest();
        long inserted = 0;
        int slotCount = access.run.advertisedSlots();
        // Match Transfer API stacking order: occupied slots first, then all positions bottom-up.
        for (int pass = 0; pass < 2; pass++) {
            for (int slot = 0; slot < slotCount && inserted < maxAmount; slot++) {
                if (pass == 0 && ledger.currentStack(slot, access.run).isEmpty()) continue;
                inserted += ledger.insertSlot(slot, resource, maxAmount - inserted, transaction,
                        access.run, insertion);
            }
        }
        return inserted;
    }

    @Override
    public long extract(ItemVariant resource, long maxAmount, TransactionContext transaction) {
        StoragePreconditions.notBlankNotNegative(resource, maxAmount);
        if (maxAmount == 0 || RunEdit.isInProgress()) return 0;
        Access access = access();
        if (access.run == null) return 0;
        long extracted = 0;
        int slotCount = access.run.advertisedSlots();
        for (int slot = 0; slot < slotCount && extracted < maxAmount; slot++) {
            extracted += access.ledger.extractSlot(slot, resource, maxAmount - extracted,
                    transaction, access.run);
        }
        return extracted;
    }

    /** Resolves one current run and its base ledger for this operation only. */
    private Access access() {
        StackRunItemAccess access = blockEntity.itemRun();
        if (access instanceof StackRun<?> run) {
            StackBlockEntity base = run.blocks.get(0);
            return new Access(((FabricStorageOwner) base).someStacksStorage(), run);
        }
        return new Access(this, access);
    }

    private record Access(FabricRunItemStorage ledger, StackRunItemAccess run) {}

    /** Reads this ledger directly after its owner has been resolved by the caller. */
    private ItemStack currentStack(int slot, StackRunItemAccess run) {
        ItemStack pending = staged.get(slot);
        return pending != null ? pending : run != null ? run.getSlot(slot) : ItemStack.EMPTY;
    }

    /** A standalone slot view must resolve again each time; it may outlive a split or growth. */
    private ItemStack currentStack(int slot) {
        Access access = access();
        return access.ledger.currentStack(slot, access.run);
    }

    private boolean isValid(ItemStack stack) {
        return blockEntity.acceptsAutomation(stack);
    }

    private int capacity(ItemVariant resource) {
        return isStructural() ? 1 : resource.getItem().getMaxStackSize();
    }

    private boolean isStructural() {
        return blockEntity.automationSlotLimit() == 1;
    }

    /** Records a slot's original live value once, then replaces its transaction-visible value. */
    private void stage(int slot, ItemStack stack, TransactionContext transaction, StackRunItemAccess run) {
        updateSnapshots(transaction);
        originals.computeIfAbsent(slot, ignored -> run.getSlot(slot).copy());
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
                if (ItemStack.isSameItemSameTags(original, result)) {
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

    private long insertSlot(int slot, ItemVariant resource, long maxAmount, TransactionContext transaction,
                            StackRunItemAccess run, StackRunItemAccess.InsertionTest insertion) {
        if (run == null || slot < 0 || slot >= run.advertisedSlots() || maxAmount == 0
                || RunEdit.isInProgress() || isStructural()
                && structuralExtractionSlot >= 0 && structuralExtractionSlot != slot) {
            return 0;
        }
        ItemStack current = currentStack(slot, run);
        if (!current.isEmpty() && !resource.matches(current)) return 0;
        int amount = (int) Math.min(maxAmount, capacity(resource) - current.getCount());
        if (amount <= 0) return 0;
        ItemStack offered = resource.toStack(amount);
        if (!isValid(offered)) return 0;
        int accepted = insertion != null ? insertion.test(slot, offered) : run.insertAt(slot, offered, true);
        if (accepted <= 0) return 0;

        ItemStack result = current.isEmpty() ? offered : current.copy();
        if (!current.isEmpty()) result.grow(amount);
        stage(slot, result, transaction, run);
        return amount;
    }

    private long extractSlot(int slot, ItemVariant resource, long maxAmount,
                             TransactionContext transaction, StackRunItemAccess run) {
        if (run == null || slot < 0 || slot >= run.advertisedSlots() || maxAmount == 0
                || RunEdit.isInProgress() || isStructural()
                && structuralExtractionSlot >= 0 && structuralExtractionSlot != slot) {
            return 0;
        }
        ItemStack current = currentStack(slot, run);
        if (current.isEmpty() || !resource.matches(current)) return 0;
        int amount = (int) Math.min(maxAmount, current.getCount());
        ItemStack result = current.copy();
        result.shrink(amount);
        stage(slot, result, transaction, run);
        if (isStructural()) structuralExtractionSlot = slot;
        return amount;
    }

    private final class RunSlot implements SingleSlotStorage<ItemVariant> {
        private final int slot;

        private RunSlot(int slot) {
            this.slot = slot;
        }

        @Override
        public long insert(ItemVariant resource, long maxAmount, TransactionContext transaction) {
            StoragePreconditions.notBlankNotNegative(resource, maxAmount);
            Access access = access();
            if (access.run == null) return 0;
            return access.ledger.insertSlot(slot, resource, maxAmount, transaction, access.run, null);
        }

        @Override
        public long extract(ItemVariant resource, long maxAmount, TransactionContext transaction) {
            StoragePreconditions.notBlankNotNegative(resource, maxAmount);
            Access access = access();
            return access.ledger.extractSlot(slot, resource, maxAmount, transaction, access.run);
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
