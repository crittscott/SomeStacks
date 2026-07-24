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
 * <p>Unlike an ordinary inventory, a position here is a place in a structure. Insertion therefore
 * ignores the requested slot and lets the column choose the lowest supported empty cell, growing
 * upward when it runs out; extraction empties the requested cell and lets the column fall down over
 * it, which is the player's own removal and drops nothing.
 *
 * <p>The slot count is the column's potential height, not its current one, so growing and shrinking
 * never changes the shape of the handler under a machine that is reading it.
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

        // A real insertion shrinks what it is handed by the amount it placed, leaving the
        // remainder; a simulation reports the count and leaves the stack alone.
        ItemStack offered = stack.copy();
        int placed = column.insert(offered, simulate);

        if (placed >= stack.getCount()) {
            return ItemStack.EMPTY;
        }
        return simulate
                ? ItemHandlerHelper.copyStackWithSize(stack, stack.getCount() - placed)
                : offered;
    }

    @Nonnull
    @Override
    public ItemStack extractItem(int slot, int amount, boolean simulate) {
        SinglesColumn column = blockEntity.column();
        if (column == null) {
            return ItemStack.EMPTY;
        }
        return column.extract(slot, amount, simulate);
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
