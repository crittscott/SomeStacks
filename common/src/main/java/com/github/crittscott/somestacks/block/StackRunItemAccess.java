package com.github.crittscott.somestacks.block;

import net.minecraft.world.item.ItemStack;

/** Positional whole-run operations exposed to loader-native automation adapters. */
public interface StackRunItemAccess {
    /**
     * Positions visible to automation, numbered from the bottom block upward. Below the height
     * limit this includes one block of headroom; insertion still decides whether growth is
     * currently possible.
     */
    int advertisedSlots();

    /** The current stack in a run position, or empty for headroom or an invalid position. */
    ItemStack getSlot(int slot);

    /**
     * Returns how many items the named advertised position accepts. When {@code simulate} is true,
     * reports the same answer without changing the run or growing it.
     */
    int insertAt(int slot, ItemStack stack, boolean simulate);

    /**
     * Extracts up to {@code amount} from the named current position, or only reports the result
     * when {@code simulate} is true. Structural runs perform their normal gravity or backfill on a
     * committed extraction.
     */
    ItemStack extract(int slot, int amount, boolean simulate);

    /** Schedules the run's deferred publication or settlement pass. */
    void markDirty();
}
