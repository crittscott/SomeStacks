package com.github.crittscott.somestacks.block;

import net.minecraft.world.item.ItemStack;

import java.util.function.IntFunction;

/**
 * Positional whole-run operations exposed to loader-native automation adapters.
 *
 * <p>Stacks returned by {@link #getSlot} are the live stored objects: callers must not modify them,
 * and must copy one before keeping or editing it. A committed {@link #insertAt} may keep the
 * caller's stack object, so the caller gives it up or passes a copy. Stacks returned by
 * {@link #extract} belong to the caller.
 */
public interface StackRunItemAccess {
    /**
     * Positions visible to automation, numbered from the bottom block upward. Below the height
     * limit this includes one block of headroom; insertion still decides whether growth is
     * currently possible.
     */
    int advertisedSlots();

    /**
     * The live stack in a run position, or empty for headroom or an invalid position. The result
     * must not be modified.
     */
    ItemStack getSlot(int slot);

    /**
     * How many of {@code stack} the named advertised position would accept if every run position,
     * headroom included, held what {@code contents} reports. Transaction adapters pass their staged
     * view so one transaction can build on its own pending changes. Whether the run may grow into
     * headroom is still read from the live world.
     */
    int acceptsAt(int slot, ItemStack stack, IntFunction<ItemStack> contents);

    /**
     * Returns how many items the named advertised position accepts. When {@code simulate} is true,
     * reports the same answer without changing the run or growing it. A committed insertion may
     * store {@code stack} itself rather than a copy.
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
