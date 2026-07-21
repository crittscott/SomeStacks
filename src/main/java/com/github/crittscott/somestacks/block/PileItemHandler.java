package com.github.crittscott.somestacks.block;

import net.minecraft.world.item.ItemStack;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemHandlerHelper;

import javax.annotation.Nonnull;

/**
 * Pile-aware item handler exposed to automation. Slots 0..26 are the connected block's
 * real inventory. While the pile can still grow upward, an extra block's worth of
 * always-empty "overflow" slots (27..53) is advertised, so automation that gauges
 * capacity by reading slots (Mekanism, AE2, ...) sees headroom rather than a full
 * block. Inserting into any slot runs the normal pile deposit, which fills real slots
 * and creates blocks up the column as needed, so the advertised room becomes real.
 */
public class PileItemHandler implements IItemHandler {
    private static final int LOCAL_SLOTS = 27;
    private static final int OVERFLOW_SLOTS = 27;

    private final StorageStackBE blockEntity;

    public PileItemHandler(StorageStackBE blockEntity) {
        this.blockEntity = blockEntity;
    }

    @Override
    public int getSlots() {
        return blockEntity.canOverflowUpward() ? LOCAL_SLOTS + OVERFLOW_SLOTS : LOCAL_SLOTS;
    }

    @Nonnull
    @Override
    public ItemStack getStackInSlot(int slot) {
        if (slot < 0 || slot >= LOCAL_SLOTS) {
            return ItemStack.EMPTY;
        }
        return blockEntity.getSlotDirect(slot);
    }

    @Nonnull
    @Override
    public ItemStack insertItem(int slot, @Nonnull ItemStack stack, boolean simulate) {
        if (stack.isEmpty()) {
            return ItemStack.EMPTY;
        }

        if (simulate) {
            int accepted = blockEntity.simulateDeposit(stack);
            return accepted >= stack.getCount()
                    ? ItemStack.EMPTY
                    : ItemHandlerHelper.copyStackWithSize(stack, stack.getCount() - accepted);
        }

        ItemStack toInsert = stack.copy();
        blockEntity.deposit(toInsert);
        return toInsert;
    }

    @Nonnull
    @Override
    public ItemStack extractItem(int slot, int amount, boolean simulate) {
        if (slot < 0 || slot >= LOCAL_SLOTS) {
            return ItemStack.EMPTY;
        }

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
        return StorageStackBE.isValidStorageItem(stack);
    }
}
