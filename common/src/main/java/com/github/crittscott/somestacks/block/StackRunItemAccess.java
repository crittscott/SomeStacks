package com.github.crittscott.somestacks.block;

import net.minecraft.world.item.ItemStack;

/** Positional whole-run operations exposed to loader-native automation adapters. */
public interface StackRunItemAccess {
    int advertisedSlots();

    ItemStack getSlot(int slot);

    /** Returns how many items the named slot accepted. */
    int insertAt(int slot, ItemStack stack, boolean simulate);

    ItemStack extract(int slot, int amount, boolean simulate);
}
