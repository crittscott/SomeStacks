package com.github.crittscott.somestacks.block;

import net.minecraft.world.item.ItemStack;

import java.util.function.IntFunction;

/** Positional whole-run operations exposed to loader-native automation adapters. */
public interface StackRunItemAccess {
    /**
     * Positions visible to automation, numbered from the bottom block upward. Below the height
     * limit this includes one block of headroom; insertion still decides whether growth is
     * currently possible.
     */
    int advertisedSlots();

    /**
     * The current stack in a run position, or empty for headroom or an invalid position. The
     * result is the live stored stack, not a copy: callers must not modify it, and must copy it
     * before keeping or editing it.
     */
    ItemStack getSlot(int slot);

    /**
     * Returns how many items the named advertised position accepts. When {@code simulate} is true,
     * reports the same answer without changing the run or growing it. A committed insertion may
     * keep {@code stack} itself as the stored stack, so the caller must not modify it afterward.
     */
    int insertAt(int slot, ItemStack stack, boolean simulate);

    /**
     * Returns how many items the named advertised position would accept if every run position
     * held what {@code contents} reports for it, including support from the positions beneath.
     * Loader transactions pass their staged view; growth checks still read the live world.
     * {@code insertAt(slot, stack, true)} answers this for the run's own contents.
     */
    int acceptsAt(int slot, ItemStack stack, IntFunction<ItemStack> contents);

    /**
     * Extracts up to {@code amount} from the named current position, or only reports the result
     * when {@code simulate} is true. Structural runs perform their normal gravity or backfill on a
     * committed extraction.
     */
    ItemStack extract(int slot, int amount, boolean simulate);

    /** Schedules the run's deferred publication or settlement pass. */
    void markDirty();
}
