package com.github.crittscott.somestacks.block;

import net.minecraft.world.item.ItemStack;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemHandlerHelper;

import javax.annotation.Nonnull;

/**
 * The whole Singles column, exposed to automation from any block in it. Positions run from the
 * bottom block's first cell upward, so a pipe under the column and one halfway up address the same
 * items.
 *
 * <p>A cell here is a place in a structure rather than a place in a bag, and every operation
 * addresses the cell it is given. {@code extractItem} empties it and lets the column fall down over
 * it, which is the player's own removal and drops nothing; {@code insertItem} places one item there
 * when the cell is empty and supported and refuses it otherwise, growing the column where the cell
 * lies in the block above it. Refusing rather than choosing elsewhere is what keeps a slot index
 * meaning one place, and no insertion can leave an item hanging in the air.
 *
 * <p>Because insertion answers for one cell, a caller that walks the range and sums what each
 * accepts gets the column's real capacity, and {@code getSlotLimit} of one is the truth about how
 * much a single call will take. A caller walking in ascending order still fills the column: each
 * placement stands before the next cell is offered.
 *
 * <p>A mutation refuses outright while another one is running; see {@link RunEdit}.
 *
 * <p>The slot count is what the column holds plus one block's worth of headroom while the
 * configured height allows another block, so it grows and shrinks with the column. See
 * {@link SinglesColumn#advertisedSlots()} for why it is neither the potential height nor the real
 * one.
 */
public class SinglesColumnHandler implements IItemHandler {
    private final SinglesStackBE blockEntity;

    public SinglesColumnHandler(SinglesStackBE blockEntity) {
        this.blockEntity = blockEntity;
    }

    @Override
    public int getSlots() {
        SinglesColumn column = blockEntity.column();
        return column != null ? column.advertisedSlots() : SinglesStackBE.SLOTS;
    }

    @Nonnull
    @Override
    public ItemStack getStackInSlot(int slot) {
        SinglesColumn column = blockEntity.column();
        if (column == null) {
            return localSlot(slot);
        }
        return column.getSlot(slot);
    }

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

        // A cell takes one item, which is what getSlotLimit says, so one call places at most one.
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

    @Override
    public int getSlotLimit(int slot) {
        return 1;
    }

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
