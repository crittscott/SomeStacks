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
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Fabric Transfer API view over a complete Storage pile, Singles column, or Bar column.
 * Slot changes are staged in the Fabric transaction and applied through the shared run mutations
 * only after its outer commit, so an aborted transaction never edits the world. Insertion checks
 * the staged contents, so one transaction can build on its own pending changes. Singles and Bar
 * permit one structural extraction position, without other pending positions or later insertion,
 * because gravity and backfill remap the slots at commit.
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
        StackRunItemAccess run = blockEntity.itemRun();
        return run != null ? run.advertisedSlots() : blockEntity.getItems().getSlots();
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
        FabricRunItemStorage base = ledger();
        if (base != this) return base.iterator();
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
        FabricRunItemStorage base = ledger();
        if (base != this) return base.insert(resource, maxAmount, transaction);
        if (!(blockEntity.itemRun() instanceof StackRun<?> run)) return 0;
        try (var query = run.growthQuery()) {
            return StorageUtil.insertStacking(getSlots(), resource, maxAmount, transaction);
        }
    }

    @Override
    public long extract(ItemVariant resource, long maxAmount, TransactionContext transaction) {
        FabricRunItemStorage base = ledger();
        if (base != this) return base.extract(resource, maxAmount, transaction);
        StoragePreconditions.notBlankNotNegative(resource, maxAmount);
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
            return base == blockEntity ? this : FabricItemStorage.of(base);
        }
        return this;
    }

    private ItemStack currentStack(int slot) {
        FabricRunItemStorage ledger = ledger();
        if (ledger != this) {
            return ledger.currentStack(slot);
        }
        ItemStack pending = staged.get(slot);
        return pending != null ? pending : liveStack(slot);
    }

    private ItemStack liveStack(int slot) {
        if (blockEntity.isRemoved()) return ItemStack.EMPTY;
        StackRunItemAccess run = blockEntity.itemRun();
        if (run != null) {
            return run.getSlot(slot);
        }
        return ItemStack.EMPTY;
    }

    /** How much of {@code stack} the run would accept at {@code slot} given the staged contents. */
    private int acceptedAt(int slot, ItemStack stack) {
        if (blockEntity.isRemoved()) return 0;
        StackRunItemAccess run = blockEntity.itemRun();
        return run != null ? run.acceptsAt(slot, stack, this::currentStack) : 0;
    }

    /** Whether any slot other than {@code slot} has a staged net change. */
    private boolean otherSlotPending(int slot) {
        for (Map.Entry<Integer, ItemStack> entry : staged.entrySet()) {
            if (entry.getKey() != slot && !ItemStack.matches(entry.getValue(), originals.get(entry.getKey()))) {
                return true;
            }
        }
        return false;
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
     * Applies each touched slot's net difference through shared run mutations after the outer
     * transaction commits: every net removal first, then net additions in ascending slot order, so
     * staged supporting cells exist before the cells above them. The run is resolved afresh for each
     * operation because growth changes it.
     */
    @Override
    protected void onFinalCommit() {
        LinkedHashMap<Integer, ItemStack> before = originals;
        Map<Integer, ItemStack> after = staged;
        originals = new LinkedHashMap<>();
        staged = new HashMap<>();
        structuralExtractionSlot = -1;

        if (!RunEdit.begin()) {
            throw new IllegalStateException("Reentrant transfer commit at " + blockEntity.getBlockPos());
        }
        try {
            for (Map.Entry<Integer, ItemStack> entry : before.entrySet()) {
                ItemStack old = entry.getValue();
                ItemStack result = after.get(entry.getKey());
                int removed = ItemStack.isSameItemSameComponents(old, result)
                        ? old.getCount() - result.getCount() : old.getCount();
                if (removed > 0
                        && blockEntity.itemRun().extract(entry.getKey(), removed, false).getCount() != removed) {
                    throw new IllegalStateException("Committed extraction changed capacity at "
                            + blockEntity.getBlockPos() + " slot " + entry.getKey());
                }
            }
            for (int slot : before.keySet().stream().sorted().toList()) {
                ItemStack old = before.get(slot);
                ItemStack result = after.get(slot);
                int added = ItemStack.isSameItemSameComponents(old, result)
                        ? result.getCount() - old.getCount() : result.getCount();
                if (added > 0
                        && blockEntity.itemRun().insertAt(slot, result.copyWithCount(added), false) != added) {
                    throw new IllegalStateException("Committed insertion changed capacity at "
                            + blockEntity.getBlockPos() + " slot " + slot);
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
            FabricRunItemStorage ledger = ledger();
            if (ledger != FabricRunItemStorage.this) {
                return slot < ledger.getSlotCount()
                        ? ledger.getSlot(slot).insert(resource, maxAmount, transaction) : 0;
            }
            StoragePreconditions.notBlankNotNegative(resource, maxAmount);
            if (maxAmount == 0 || RunEdit.isInProgress() || structuralExtractionSlot >= 0) {
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
            if (!isValid(offered)) {
                return 0;
            }
            amount = Math.min(amount, acceptedAt(slot, offered));
            if (amount <= 0) {
                return 0;
            }
            offered.setCount(amount);

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
            if (maxAmount == 0 || RunEdit.isInProgress() || isStructural()
                    && (structuralExtractionSlot >= 0 && structuralExtractionSlot != slot
                    || otherSlotPending(slot))) {
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
