package com.github.crittscott.somestacks.block;

import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.IItemHandler;

import javax.annotation.Nonnull;

/**
 * The whole Singles column, exposed to automation from any block in it. Positions run from the
 * bottom block's first cell upward. Every operation addresses the cell it is given: extraction
 * empties it and lets the column fall over it, insertion places one item there when the cell is
 * empty and supported and refuses it otherwise, growing the column where the cell lies in the block
 * above. A mutation is refused while another is running; see {@link RunEdit}.
 */
public class SinglesColumnHandler implements IItemHandler {
    private final SinglesStackBE blockEntity;

    /** Creates a view whose column is resolved from this block entity on each access. */
    public SinglesColumnHandler(SinglesStackBE blockEntity) {
        this.blockEntity = blockEntity;
    }

    /**
     * Bottom-up column positions, including one prospective block while growth is advertised.
     * Without a server-side column, returns only this block's local slot count.
     */
    @Override
    public int getSlots() {
        SinglesColumn column = blockEntity.column();
        return column != null ? column.advertisedSlots() : SinglesStackBE.SLOTS;
    }

    /**
     * Returns live contents, or local contents without a column; headroom and invalid
     * positions are empty. Callers must not modify the result.
     */
    @Nonnull
    @Override
    public ItemStack getStackInSlot(int slot) {
        SinglesColumn column = blockEntity.column();
        if (column == null) {
            return localSlot(slot);
        }
        return column.getSlot(slot);
    }

    /**
     * Inserts at one bottom-up position, leaving the offered stack unchanged and returning its
     * remainder. Simulation changes nothing and cannot promise acceptance by later event hooks.
     * An unavailable column or a reentrant committed edit returns the complete offer.
     */
    @Nonnull
    @Override
    public ItemStack insertItem(int slot, @Nonnull ItemStack stack, boolean simulate) {
        if (stack.isEmpty()) {
            return ItemStack.EMPTY;
        }

        SinglesColumn column = blockEntity.column();
        if (column == null) {
            return stack;
        }

        boolean placed;
        if (simulate) {
            placed = column.insertOneAt(slot, stack, true);
        } else {
            if (!RunEdit.begin()) {
                return stack;
            }
            try {
                placed = column.insertOneAt(slot, stack, false);
            } finally {
                RunEdit.end();
            }
        }

        if (!placed) {
            return stack;
        }
        return stack.getCount() == 1
                ? ItemStack.EMPTY
                : stack.copyWithCount(stack.getCount() - 1);
    }

    /**
     * Extracts from one bottom-up position, returning the removed contents; simulation changes
     * nothing. An unavailable column or a reentrant committed edit returns empty. A committed extraction drops the visual column above by one cell.
     */
    @Nonnull
    @Override
    public ItemStack extractItem(int slot, int amount, boolean simulate) {
        SinglesColumn column = blockEntity.column();
        if (column == null) {
            return ItemStack.EMPTY;
        }
        if (simulate) {
            return column.extract(slot, amount, true);
        }
        if (!RunEdit.begin()) {
            return ItemStack.EMPTY;
        }
        try {
            return column.extract(slot, amount, false);
        } finally {
            RunEdit.end();
        }
    }

    /** One item per position, independent of occupancy. */
    @Override
    public int getSlotLimit(int slot) {
        return 1;
    }

    /** Admission only: ignores position, capacity, support, enablement for growth, and edit authority. */
    @Override
    public boolean isItemValid(int slot, @Nonnull ItemStack stack) {
        return SinglesStackBE.isValidSinglesItem(stack);
    }

    /** Client-side fallback: no column resolves there, so the handler describes this block alone. */
    private ItemStack localSlot(int slot) {
        if (slot < 0 || slot >= SinglesStackBE.SLOTS) {
            return ItemStack.EMPTY;
        }
        return blockEntity.getItems().getStackInSlot(slot);
    }
}
