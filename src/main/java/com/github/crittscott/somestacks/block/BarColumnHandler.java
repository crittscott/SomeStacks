package com.github.crittscott.somestacks.block;

import net.minecraft.world.item.ItemStack;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemHandlerHelper;

import javax.annotation.Nonnull;

/**
 * The whole Bar column, exposed to automation from any block in it. Positions run from the bottom
 * block's first cell upward, so a pipe under the column and one halfway up address the same bars.
 *
 * <p>Unlike an ordinary inventory, a position here is a place in a structure. Insertion therefore
 * ignores the requested slot and lets the column choose the lowest supported empty position,
 * growing upward when it runs out; extraction takes the requested bar and fills the hole from the
 * top. Both leave a standing structure, which is why automation never drops bars the way a player's
 * own extraction does.
 *
 * <p>The slot count is the column's potential height, not its current one, so growing and shrinking
 * never changes the shape of the handler under a machine that is reading it.
 */
public class BarColumnHandler implements IItemHandler {
    private final BarStackBE blockEntity;

    public BarColumnHandler(BarStackBE blockEntity) {
        this.blockEntity = blockEntity;
    }

    @Override
    public int getSlots() {
        BarColumn column = blockEntity.column();
        return column != null ? column.advertisedSlots() : BarStackBE.SLOTS;
    }

    @Nonnull
    @Override
    public ItemStack getStackInSlot(int slot) {
        BarColumn column = blockEntity.column();
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

        BarColumn column = blockEntity.column();
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
        BarColumn column = blockEntity.column();
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
