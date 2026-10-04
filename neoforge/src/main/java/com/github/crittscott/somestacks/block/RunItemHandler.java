package com.github.crittscott.somestacks.block;

import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.IItemHandler;

import javax.annotation.Nonnull;

/** NeoForge item-handler view over whichever complete run owns the attached stack block. */
public final class RunItemHandler implements IItemHandler {
    private final StackBlockEntity blockEntity;

    public RunItemHandler(StackBlockEntity blockEntity) {
        this.blockEntity = blockEntity;
    }

    @Override
    public int getSlots() {
        StackRunItemAccess run = blockEntity.itemRun();
        return run != null ? run.advertisedSlots() : blockEntity.getItems().getSlots();
    }

    @Nonnull
    @Override
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
    @Override
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
    @Override
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

    @Override
    public int getSlotLimit(int slot) {
        return blockEntity.automationSlotLimit();
    }

    @Override
    public boolean isItemValid(int slot, @Nonnull ItemStack stack) {
        return blockEntity.acceptsAutomation(stack);
    }
}
