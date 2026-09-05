package com.github.crittscott.somestacks.block;

import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.IItemHandler;

import javax.annotation.Nonnull;

/**
 * The whole pile, exposed to automation from any block in it. Slots run from the base block's
 * first slot upward. Every operation is positional; a slot in the block above the pile is where
 * insertion grows the pile. A mutation is refused while another is running; see {@link RunEdit}.
 */
public class PileItemHandler implements IItemHandler {
    private final StorageStackBE blockEntity;

    public PileItemHandler(StorageStackBE blockEntity) {
        this.blockEntity = blockEntity;
    }

    @Override
    public int getSlots() {
        StoragePile pile = blockEntity.pile();
        return pile != null ? pile.advertisedSlots() : StorageStackBE.SLOTS;
    }

    @Nonnull
    @Override
    public ItemStack getStackInSlot(int slot) {
        StoragePile pile = blockEntity.pile();
        if (pile == null) {
            return localSlot(slot);
        }
        return pile.getSlot(slot);
    }

    @Nonnull
    @Override
    public ItemStack insertItem(int slot, @Nonnull ItemStack stack, boolean simulate) {
        if (stack.isEmpty()) {
            return ItemStack.EMPTY;
        }

        StoragePile pile = blockEntity.pile();
        if (pile == null) {
            return stack;
        }

        int accepted;
        if (simulate) {
            accepted = pile.insertAt(slot, stack, true);
        } else {
            if (!RunEdit.begin()) {
                return stack;
            }
            try {
                accepted = pile.insertAt(slot, stack, false);
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
        StoragePile pile = blockEntity.pile();
        if (pile == null) {
            return ItemStack.EMPTY;
        }

        if (simulate) {
            ItemStack inSlot = pile.getSlot(slot);
            if (inSlot.isEmpty()) {
                return ItemStack.EMPTY;
            }
            return inSlot.copyWithCount(Math.min(amount, inSlot.getCount()));
        }

        if (!RunEdit.begin()) {
            return ItemStack.EMPTY;
        }
        try {
            return pile.extract(slot, amount);
        } finally {
            RunEdit.end();
        }
    }

    @Override
    public int getSlotLimit(int slot) {
        return 64;
    }

    @Override
    public boolean isItemValid(int slot, @Nonnull ItemStack stack) {
        return StorageStackBE.isValidStorageItem(stack);
    }

    /** Client-side fallback: no pile resolves there, so the handler describes this block alone. */
    private ItemStack localSlot(int slot) {
        if (slot < 0 || slot >= StorageStackBE.SLOTS) {
            return ItemStack.EMPTY;
        }
        return blockEntity.getItems().getStackInSlot(slot);
    }
}
