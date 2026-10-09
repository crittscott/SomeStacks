package com.github.crittscott.somestacks.block;

import net.minecraft.world.item.ItemStack;

import javax.annotation.Nonnull;

/** Shared item-handler behavior over whichever complete run owns the attached stack block. */
public abstract class RunItemHandlerBase {
    private final StackBlockEntity blockEntity;

    protected RunItemHandlerBase(StackBlockEntity blockEntity) {
        this.blockEntity = blockEntity;
    }

    public int getSlots() {
        StackRunItemAccess run = blockEntity.itemRun();
        return run != null ? run.advertisedSlots() : blockEntity.getItems().getSlots();
    }

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

    public int getSlotLimit(int slot) {
        return blockEntity.automationSlotLimit();
    }

    public boolean isItemValid(int slot, @Nonnull ItemStack stack) {
        return blockEntity.acceptsAutomation(stack);
    }
}
