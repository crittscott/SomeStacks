package com.github.crittscott.somestacks.block;

import net.minecraft.world.item.ItemStack;

import javax.annotation.Nonnull;

/** Shared item-handler behavior over whichever complete run owns the attached stack block. */
public abstract class RunItemHandlerBase {
    private final StackBlockEntity blockEntity;

    /** Creates a view whose run is resolved from this block entity on each access. */
    protected RunItemHandlerBase(StackBlockEntity blockEntity) {
        this.blockEntity = blockEntity;
    }

    /**
     * Bottom-up run positions, including one prospective block while growth is advertised.
     * Without a server run, returns only this block's local slot count.
     */
    public int getSlots() {
        StackRunItemAccess run = blockEntity.itemRun();
        return run != null ? run.advertisedSlots() : blockEntity.getItems().getSlots();
    }

    /** Returns read-only live contents, or local contents without a run; headroom and invalid positions are empty. */
    @Nonnull
    public ItemStack getStackInSlot(int slot) {
        StackRunItemAccess run = blockEntity.itemRun();
        if (run != null) {
            return run.getSlot(slot);
        }
        return slot >= 0 && slot < blockEntity.getItems().getSlots()
                ? blockEntity.getItems().getStackInSlot(slot)
                : ItemStack.EMPTY;
    }

    /**
     * Inserts at one bottom-up run position, leaving the offered stack unchanged and returning its
     * remainder. Simulation changes nothing and cannot promise acceptance by later event hooks.
     * Unavailable runs and reentrant committed edits return the complete offer.
     */
    @Nonnull
    public ItemStack insertItem(int slot, @Nonnull ItemStack stack, boolean simulate) {
        if (stack.isEmpty()) {
            return ItemStack.EMPTY;
        }
        StackRunItemAccess run = blockEntity.itemRun();
        if (run == null) {
            return stack;
        }

        int accepted;
        if (simulate) {
            accepted = run.insertAt(slot, stack, true);
        } else {
            if (!RunEdit.begin()) {
                return stack;
            }
            try {
                accepted = run.insertAt(slot, stack, false);
            } finally {
                RunEdit.end();
            }
        }
        return accepted >= stack.getCount()
                ? ItemStack.EMPTY
                : stack.copyWithCount(stack.getCount() - accepted);
    }

    /**
     * Extracts from a bottom-up run position, returning the removed contents; simulation changes
     * nothing. Unavailable runs and reentrant committed edits return empty. Structural movement
     * follows the concrete run's extraction rules.
     */
    @Nonnull
    public ItemStack extractItem(int slot, int amount, boolean simulate) {
        StackRunItemAccess run = blockEntity.itemRun();
        if (run == null) {
            return ItemStack.EMPTY;
        }
        if (simulate) {
            return run.extract(slot, amount, true);
        }
        if (!RunEdit.begin()) {
            return ItemStack.EMPTY;
        }
        try {
            return run.extract(slot, amount, false);
        } finally {
            RunEdit.end();
        }
    }

    /** Per-position capacity before the offered item's own stack limit, independent of slot occupancy. */
    public int getSlotLimit(int slot) {
        return blockEntity.localSlotLimit();
    }

    /** Admission only: ignores position, capacity, support, enablement for growth, and edit authority. */
    public boolean isItemValid(int slot, @Nonnull ItemStack stack) {
        return blockEntity.isStoredItemValid(stack);
    }
}
