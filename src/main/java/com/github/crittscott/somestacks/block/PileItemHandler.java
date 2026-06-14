package com.github.crittscott.somestacks.block;

import net.minecraft.world.item.ItemStack;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemHandlerHelper;

import javax.annotation.Nonnull;

public class PileItemHandler implements IItemHandler {
    private final StorageStackBE blockEntity;

    public PileItemHandler(StorageStackBE blockEntity) {
        this.blockEntity = blockEntity;
    }

    @Override
    public int getSlots() {
        return 27;
    }

    @Nonnull
    @Override
    public ItemStack getStackInSlot(int slot) {
        return blockEntity.getSlotDirect(slot);
    }

    @Nonnull
    @Override
    public ItemStack insertItem(int slot, @Nonnull ItemStack stack, boolean simulate) {
        if (simulate) {
            ItemStack inSlot = blockEntity.getSlotDirect(slot);
            if (inSlot.isEmpty()) {
                int toInsert = Math.min(stack.getCount(), stack.getMaxStackSize());
                return stack.getCount() > toInsert ?
                        ItemHandlerHelper.copyStackWithSize(stack, stack.getCount() - toInsert) :
                        ItemStack.EMPTY;
            } else if (ItemStack.isSameItemSameTags(inSlot, stack)) {
                int space = inSlot.getMaxStackSize() - inSlot.getCount();
                int toInsert = Math.min(stack.getCount(), space);
                return stack.getCount() > toInsert ?
                        ItemHandlerHelper.copyStackWithSize(stack, stack.getCount() - toInsert) :
                        ItemStack.EMPTY;
            }
            return stack;
        }

        ItemStack toInsert = stack.copy();
        int deposited = blockEntity.deposit(toInsert);
        return toInsert;
    }

    @Nonnull
    @Override
    public ItemStack extractItem(int slot, int amount, boolean simulate) {
        if (simulate) {
            ItemStack inSlot = blockEntity.getSlotDirect(slot);
            if (inSlot.isEmpty()) {
                return ItemStack.EMPTY;
            }
            int toExtract = Math.min(amount, inSlot.getCount());
            return ItemHandlerHelper.copyStackWithSize(inSlot, toExtract);
        }

        return blockEntity.extractFromSlot(slot, amount);
    }

    @Override
    public int getSlotLimit(int slot) {
        return 64;
    }

    @Override
    public boolean isItemValid(int slot, @Nonnull ItemStack stack) {
        return true;
    }
}
