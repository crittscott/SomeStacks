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
     * returned stack is the live stored object: callers must not modify it, and must copy it before
     * keeping or editing it.
     */
    ItemStack getSlot(int slot);

    /**
     * How many of {@code stack} the named advertised position would accept if every run position
     * held what {@code contents} reports for it. Admission, slot room and support read
     * {@code contents}; growth into headroom is checked against the live world. This lets a staged
     * transaction ask about the contents it would commit rather than the contents the world holds.
     */
    int acceptsAt(int slot, ItemStack stack, IntFunction<ItemStack> contents);

    /**
     * Returns how many items the named advertised position accepts. When {@code simulate} is true,
     * reports the same answer without changing the run or growing it. A committed insertion may
     * keep {@code stack} itself or a copy of it, so the caller must give it up or pass a copy.
     */
    int insertAt(int slot, ItemStack stack, boolean simulate);

    /**
     * Extracts up to {@code amount} from the named current position, or only reports the result
     * when {@code simulate} is true. Structural runs perform their normal gravity or backfill on a
     * committed extraction. The returned stack belongs to the caller.
     */
    ItemStack extract(int slot, int amount, boolean simulate);

    /** Schedules the run's deferred publication or settlement pass. */
    void markDirty();
}
