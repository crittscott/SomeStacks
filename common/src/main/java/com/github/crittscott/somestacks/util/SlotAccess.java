package com.github.crittscott.somestacks.util;

import net.minecraft.world.item.ItemStack;

import javax.annotation.Nonnull;

/**
 * A numbered range of item slots, matching Forge's {@code IItemHandler} contract exactly so the
 * loader-specific capability views built over it stay thin adapters. This is the internal storage
 * primitive for block entities, piles, and columns; only the Forge/Fabric capability adapters that
 * expose it to automation are loader-specific.
 */
public interface SlotAccess {
    int getSlots();

    @Nonnull
    ItemStack getStackInSlot(int slot);

    @Nonnull
    ItemStack insertItem(int slot, @Nonnull ItemStack stack, boolean simulate);

    @Nonnull
    ItemStack extractItem(int slot, int amount, boolean simulate);

    int getSlotLimit(int slot);

    boolean isItemValid(int slot, @Nonnull ItemStack stack);
}
