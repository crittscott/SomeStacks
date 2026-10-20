package com.github.crittscott.somestacks.block;

import net.minecraft.world.item.ItemStack;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemHandlerHelper;

import javax.annotation.Nonnull;

/**
 * The whole Bar column, exposed to automation from any block in it. Positions run from the bottom
 * block's first cell upward, so a pipe under the column and one halfway up address the same bars.
 *
 * <p>A position here is a place in a structure rather than a place in a bag, and every operation
 * addresses the position it is given. {@code extractItem} takes that bar and fills the hole from the
 * top of the column; {@code insertItem} places one bar there when the position is empty and
 * supported and refuses it otherwise, growing the column where the position lies in the block above
 * it. Both leave a standing structure, which is why automation never drops bars the way a player's
 * own extraction does.
 *
 * <p>Because insertion addresses one position, a caller that walks the range and sums what each
 * accepts gets the column's real capacity, and {@code getSlotLimit} of one is the truth about how
 * much a single call will take. A caller walking in ascending order still fills the column: each
 * placement stands before the next position is offered.
 *
 * <p>A mutation is refused while another is running; see {@link RunEdit}.
 *
 * <p>The slot count is what the column holds plus one block's worth of headroom while the
 * configured height allows another block, so it grows and shrinks with the column. See
 * {@link BarColumn#advertisedSlots()} for why it is neither the potential height nor the real
 * one.
 */
public class BarColumnHandler implements IItemHandler {
    private final BarStackBE blockEntity;

    /** Creates a view whose column is resolved from this block entity on each access. */
    public BarColumnHandler(BarStackBE blockEntity) {
        this.blockEntity = blockEntity;
    }

    /**
     * Bottom-up column positions, including one prospective block while growth is advertised.
     * Without a server-side column, returns only this block's local slot count.
     */
    @Override
    public int getSlots() {
        BarColumn column = blockEntity.column();
        return column != null ? column.advertisedSlots() : BarStackBE.SLOTS;
    }

    /**
     * Returns live contents, or local contents without a column; headroom and invalid
     * positions are empty. Callers must not modify the result.
     */
    @Nonnull
    @Override
    public ItemStack getStackInSlot(int slot) {
        BarColumn column = blockEntity.column();
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

        BarColumn column = blockEntity.column();
        if (column == null) {
            return stack;
        }

        // A position takes one bar, which is what getSlotLimit says, so one call places at most one.
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
                : ItemHandlerHelper.copyStackWithSize(stack, stack.getCount() - 1);
    }

    /**
     * Extracts from one bottom-up position, returning the removed contents; simulation changes
     * nothing. An unavailable column or a reentrant committed edit returns empty. A committed extraction moves the column's topmost bar into the opened position.
     */
    @Nonnull
    @Override
    public ItemStack extractItem(int slot, int amount, boolean simulate) {
        BarColumn column = blockEntity.column();
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

    /** One bar per position, independent of occupancy. */
    @Override
    public int getSlotLimit(int slot) {
        return 1;
    }

    /** Admission only: ignores position, capacity, support, enablement for growth, and edit authority. */
    @Override
    public boolean isItemValid(int slot, @Nonnull ItemStack stack) {
        return BarStackBE.isValidBarItem(stack);
    }

    /** Client-side fallback: no column resolves there, so the handler describes this block alone. */
    private ItemStack localSlot(int slot) {
        if (slot < 0 || slot >= BarStackBE.SLOTS) {
            return ItemStack.EMPTY;
        }
        return blockEntity.getItems().getStackInSlot(slot);
    }
}
