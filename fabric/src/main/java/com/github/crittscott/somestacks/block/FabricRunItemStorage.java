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
import net.minecraft.world.level.block.entity.BlockEntity;

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
    private final BlockEntity blockEntity;
    private final Map<Integer, RunSlot> slotViews = new HashMap<>();
    private LinkedHashMap<Integer, ItemStack> originals = new LinkedHashMap<>();
    private Map<Integer, ItemStack> staged = new HashMap<>();
    private int structuralExtractionSlot = -1;

    FabricRunItemStorage(BlockEntity blockEntity) {
        this.blockEntity = blockEntity;
    }

    @Override
    public int getSlotCount() {
        if (blockEntity instanceof StorageStackBE be) {
            StoragePile pile = be.pile();
            return pile != null ? pile.advertisedSlots() : StorageStackBE.SLOTS;
        }
        if (blockEntity instanceof SinglesStackBE be) {
            SinglesColumn column = be.column();
            return column != null ? column.advertisedSlots() : SinglesStackBE.SLOTS;
        }
        BarStackBE be = (BarStackBE) blockEntity;
        BarColumn column = be.column();
        return column != null ? column.advertisedSlots() : BarStackBE.SLOTS;
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
        if (blockEntity instanceof StorageStackBE be) {
            StoragePile pile = be.pile();
            return pile != null ? pile.getSlot(slot) : localStack(be, slot, StorageStackBE.SLOTS);
        }
        if (blockEntity instanceof SinglesStackBE be) {
            SinglesColumn column = be.column();
            return column != null ? column.getSlot(slot) : localStack(be, slot, SinglesStackBE.SLOTS);
        }
        BarStackBE be = (BarStackBE) blockEntity;
        BarColumn column = be.column();
        return column != null ? column.getSlot(slot) : localStack(be, slot, BarStackBE.SLOTS);
    }

    private static ItemStack localStack(StorageStackBE be, int slot, int slotCount) {
        return slot >= 0 && slot < slotCount ? be.getItems().getStackInSlot(slot) : ItemStack.EMPTY;
    }

    private static ItemStack localStack(SinglesStackBE be, int slot, int slotCount) {
        return slot >= 0 && slot < slotCount ? be.getItems().getStackInSlot(slot) : ItemStack.EMPTY;
    }

    private static ItemStack localStack(BarStackBE be, int slot, int slotCount) {
        return slot >= 0 && slot < slotCount ? be.getItems().getStackInSlot(slot) : ItemStack.EMPTY;
    }

    private boolean canInsert(int slot, ItemStack stack) {
        if (blockEntity instanceof StorageStackBE be) {
            StoragePile pile = be.pile();
            return pile != null && pile.insertAt(slot, stack, true) > 0;
        }
        if (blockEntity instanceof SinglesStackBE be) {
            SinglesColumn column = be.column();
            return column != null && column.insertOneAt(slot, stack, true);
        }
        BarColumn column = ((BarStackBE) blockEntity).column();
        return column != null && column.insertOneAt(slot, stack, true);
    }

    private boolean isValid(ItemStack stack) {
        if (blockEntity instanceof StorageStackBE) {
            return StorageStackBE.isValidStorageItem(stack);
        }
        if (blockEntity instanceof SinglesStackBE) {
            return SinglesStackBE.isValidSinglesItem(stack);
        }
        return BarStackBE.isValidBarItem(stack);
    }

    private int capacity(ItemVariant resource) {
        return blockEntity instanceof StorageStackBE ? resource.getItem().getMaxStackSize() : 1;
    }

    private boolean isStructural() {
        return !(blockEntity instanceof StorageStackBE);
    }

    private void stage(int slot, ItemStack stack, TransactionContext transaction) {
        updateSnapshots(transaction);
        originals.computeIfAbsent(slot, ignored -> liveStack(slot).copy());
        staged.put(slot, stack);
    }

    private void commitInsert(int slot, ItemStack stack) {
        if (blockEntity instanceof StorageStackBE be) {
            StoragePile pile = be.pile();
            if (pile != null) {
                pile.insertAt(slot, stack, false);
            }
        } else if (blockEntity instanceof SinglesStackBE be) {
            SinglesColumn column = be.column();
            if (column != null) {
                column.insertOneAt(slot, stack, false);
            }
        } else {
            BarColumn column = ((BarStackBE) blockEntity).column();
            if (column != null) {
                column.insertOneAt(slot, stack, false);
            }
        }
    }

    private void commitExtract(int slot, int amount) {
        if (blockEntity instanceof StorageStackBE be) {
            StoragePile pile = be.pile();
            if (pile != null) {
                pile.extract(slot, amount);
            }
        } else if (blockEntity instanceof SinglesStackBE be) {
            SinglesColumn column = be.column();
            if (column != null) {
                column.extract(slot, amount, false);
            }
        } else {
            BarColumn column = ((BarStackBE) blockEntity).column();
            if (column != null) {
                column.extract(slot, amount, false);
            }
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
            return resource.isBlank() && blockEntity instanceof StorageStackBE
                    ? 64
                    : capacity(resource);
        }
    }
}
