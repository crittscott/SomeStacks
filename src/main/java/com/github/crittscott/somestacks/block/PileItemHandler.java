package com.github.crittscott.somestacks.block;

import net.minecraft.world.item.ItemStack;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemHandlerHelper;

import javax.annotation.Nonnull;

/**
 * The whole pile, exposed to automation from any block in it. Slots run from the base block's
 * first slot upward, so a hopper under the pile and an interface halfway up address the same
 * inventory and see the same contents.
 *
 * <p>The handler is positional for reading and extraction but not for insertion. {@code
 * getStackInSlot} and {@code extractItem} address the slot they are given; insertion ignores it,
 * because the pile fills from its base upward and grows the column when it needs to. A caller that
 * sums simulated per-slot capacity therefore over-counts, since every slot answers with the space
 * the whole pile has.
 *
 * <p>The slot count is what the pile holds plus one block's worth of headroom while the configured
 * height allows another block, so it grows and shrinks with the pile. See
 * {@link SinglesColumn#advertisedSlots()} for why it is neither the potential height nor the real
 * one.
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

        if (simulate) {
            int accepted = pile.simulateDeposit(stack);
            return accepted >= stack.getCount()
                    ? ItemStack.EMPTY
                    : ItemHandlerHelper.copyStackWithSize(stack, stack.getCount() - accepted);
        }

        ItemStack toInsert = stack.copy();
        pile.deposit(toInsert, null);
        return toInsert;
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
            return ItemHandlerHelper.copyStackWithSize(inSlot, Math.min(amount, inSlot.getCount()));
        }

        return pile.extract(slot, amount);
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
