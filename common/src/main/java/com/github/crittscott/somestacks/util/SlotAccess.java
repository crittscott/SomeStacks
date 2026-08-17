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
    /** The number of slots, indexed {@code [0, getSlots())}. */
    int getSlots();

    /** The stack in {@code slot}, or {@link ItemStack#EMPTY} if the slot is empty; never null. */
    @Nonnull
    ItemStack getStackInSlot(int slot);

    /**
     * Inserts as much of {@code stack} into {@code slot} as {@link #isItemValid} and
     * {@link #getSlotLimit} allow. Returns the remainder that was not accepted —
     * {@link ItemStack#EMPTY} if all of it was — and never mutates {@code stack} itself. When
     * {@code simulate} is {@code true}, the slot is left unchanged and only the would-be
     * remainder is computed.
     */
    @Nonnull
    ItemStack insertItem(int slot, @Nonnull ItemStack stack, boolean simulate);

    /**
     * Removes up to {@code amount} items from {@code slot} and returns the stack that was
     * removed — {@link ItemStack#EMPTY} if the slot was empty. When {@code simulate} is
     * {@code true}, the slot is left unchanged and only the would-be result is computed.
     */
    @Nonnull
    ItemStack extractItem(int slot, int amount, boolean simulate);

    /** The maximum stack size {@code slot} accepts, independent of the item's own max stack size. */
    int getSlotLimit(int slot);

    /** Whether {@code stack} is allowed in {@code slot} at all, before any capacity check. */
    boolean isItemValid(int slot, @Nonnull ItemStack stack);
}
