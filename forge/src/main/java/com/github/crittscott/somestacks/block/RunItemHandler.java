package com.github.crittscott.somestacks.block;

import net.minecraft.world.item.ItemStack;
import net.minecraftforge.items.IItemHandler;

import javax.annotation.Nonnull;

/** Forge item-handler view over whichever complete run owns the attached stack block. */
public final class RunItemHandler implements IItemHandler {
    private final StackBlockEntity blockEntity;

    /** Binds a native item-handler wrapper to one block whose current run is resolved on access. */
    public RunItemHandler(StackBlockEntity blockEntity) {
        this.blockEntity = blockEntity;
    }

    /** Current run positions, including headroom; unattached/client entities expose local slots. */
    @Override
    public int getSlots() {
        return blockEntity.advertisedItemSlots();
    }

    /** Current contents at a run position, or local contents when no server run is available. */
    @Nonnull
    @Override
    public ItemStack getStackInSlot(int slot) {
        StackRun<?> run = blockEntity.itemRun();
        if (run != null) {
            return run.getSlot(slot);
        }
        return slot >= 0 && slot < blockEntity.getItems().getSlots()
                ? blockEntity.getItems().getStackInSlot(slot)
                : ItemStack.EMPTY;
    }

    /** Inserts positionally without changing the caller's stack; simulation makes no edits. */
    @Nonnull
    @Override
    public ItemStack insertItem(int slot, @Nonnull ItemStack stack, boolean simulate) {
        if (stack.isEmpty()) {
            return ItemStack.EMPTY;
        }
        StackRun<?> run = blockEntity.itemRun();
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
                : remainder(stack, accepted);
    }

    /** Extracts through the concrete run's movement rules, refusing reentrant mutations. */
    @Nonnull
    @Override
    public ItemStack extractItem(int slot, int amount, boolean simulate) {
        StackRun<?> run = blockEntity.itemRun();
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

    private static ItemStack remainder(ItemStack stack, int accepted) {
        if (accepted == 0) {
            return stack;
        }
        ItemStack remainder = stack.copy();
        remainder.setCount(stack.getCount() - accepted);
        return remainder;
    }

    /** Advertised per-position capacity before the item's own stack limit. */
    @Override
    public int getSlotLimit(int slot) {
        return blockEntity.automationSlotLimit();
    }

    /** New-item admission; insertion separately checks occupancy, support, and growth. */
    @Override
    public boolean isItemValid(int slot, @Nonnull ItemStack stack) {
        return blockEntity.acceptsAutomation(stack);
    }
}
