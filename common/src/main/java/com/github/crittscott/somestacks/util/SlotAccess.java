package com.github.crittscott.somestacks.util;

import net.minecraft.world.item.ItemStack;

import javax.annotation.Nonnull;

/**
 * A read-only view of numbered local item slots for geometry, rendering, drops, and inventory reads.
 * Mutations belong to StackItemStorage; loader-native automation adapts whole runs.
 */
public interface SlotAccess {
    /** The number of slots, indexed {@code [0, getSlots())}. */
    int getSlots();

    /**
     * The stack in {@code slot}, or {@link ItemStack#EMPTY} if the slot is empty; never null. The
     * result is the live stored object: callers must not modify it, and must copy it before keeping
     * or editing it.
     */
    @Nonnull
    ItemStack getStackInSlot(int slot);
}
