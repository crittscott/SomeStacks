package com.github.crittscott.somestacks.block;

import net.minecraft.world.item.ItemStack;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemHandlerHelper;

import javax.annotation.Nonnull;

/**
 * The whole pile, exposed to automation from any block in it. Slots run from the base block's
 * first slot upward, so a hopper under the pile and an interface halfway up address the same
 * inventory and see the same contents.
 *
 * <p>Every operation is positional: {@code getStackInSlot}, {@code extractItem} and {@code
 * insertItem} all address the slot they are given, so a caller that walks the range and sums what
 * each slot accepts gets the pile's real capacity, and a simulation promises what the commit
 * delivers. A slot in the block above the pile is where insertion grows the pile.
 *
 * <p>Filling from the base upward is not lost by that: the settle an insertion schedules packs the
 * whole pile down on the next tick. It arrives a tick behind a player's own deposit, which fills
 * from the base outright.
 *
 * <p>A mutation is refused while another is running; see {@link RunEdit}.
 *
 * <p>The slot count is what the pile holds plus one block's worth of headroom while the configured
 * height allows another block, so it grows and shrinks with the pile. See
 * {@link StoragePile#advertisedSlots()} for why it is neither the potential height nor the real
 * one.
 */
public class PileItemHandler implements IItemHandler {
    private final StorageStackBE blockEntity;

    /** Creates a view whose pile is resolved from this block entity on each access. */
    public PileItemHandler(StorageStackBE blockEntity) {
        this.blockEntity = blockEntity;
    }

    /**
     * Bottom-up pile slots, including one prospective block while growth is advertised.
     * Without a server-side pile, returns only this block's local slot count.
     */
    @Override
    public int getSlots() {
        StoragePile pile = blockEntity.pile();
        return pile != null ? pile.advertisedSlots() : StorageStackBE.SLOTS;
    }

    /**
     * Returns live contents, or local contents without a pile; headroom and invalid
     * slots are empty. Callers must not modify the result.
     */
    @Nonnull
    @Override
    public ItemStack getStackInSlot(int slot) {
        StoragePile pile = blockEntity.pile();
        if (pile == null) {
            return localSlot(slot);
        }
        return pile.getSlot(slot);
    }

    /**
     * Inserts at one bottom-up slot, leaving the offered stack unchanged and returning its
     * remainder. Simulation changes nothing and cannot promise acceptance by later event hooks.
     * An unavailable pile or a reentrant committed edit returns the complete offer.
     */
    @Nonnull
    @Override
    public ItemStack insertItem(int slot, @Nonnull ItemStack stack, boolean simulate) {
        if (stack.isEmpty()) {
            return ItemStack.EMPTY;
        }

        StoragePile pile = blockEntity.pile();
        if (pile == null) {
            return stack;
        }

        int accepted;
        if (simulate) {
            accepted = pile.insertAt(slot, stack, true);
        } else {
            if (!RunEdit.begin()) {
                return stack;
            }
            try {
                accepted = pile.insertAt(slot, stack, false);
            } finally {
                RunEdit.end();
            }
        }

        return accepted >= stack.getCount()
                ? ItemStack.EMPTY
                : ItemHandlerHelper.copyStackWithSize(stack, stack.getCount() - accepted);
    }

    /**
     * Extracts from one bottom-up slot, returning the removed contents; simulation changes
     * nothing. An unavailable pile or a reentrant committed edit returns empty. A committed extraction schedules the pile's settlement.
     */
    @Nonnull
    @Override
    public ItemStack extractItem(int slot, int amount, boolean simulate) {
        StoragePile pile = blockEntity.pile();
        if (pile == null) {
            return ItemStack.EMPTY;
        }

        if (simulate) {
            ItemStack inSlot = pile.getSlot(slot);
            if (inSlot.isEmpty()) {
                return ItemStack.EMPTY;
            }
            return ItemHandlerHelper.copyStackWithSize(inSlot, Math.min(amount, inSlot.getCount()));
        }

        if (!RunEdit.begin()) {
            return ItemStack.EMPTY;
        }
        try {
            return pile.extract(slot, amount);
        } finally {
            RunEdit.end();
        }
    }

    /** Advertised per-slot capacity, independent of occupancy; the offered item's own stack limit still applies. */
    @Override
    public int getSlotLimit(int slot) {
        return 64;
    }

    /** Admission only: ignores position, capacity, support, enablement for growth, and edit authority. */
    @Override
    public boolean isItemValid(int slot, @Nonnull ItemStack stack) {
        return StorageStackBE.isValidStorageItem(stack);
    }

    /** Client-side fallback: no pile resolves there, so the handler describes this block alone. */
    private ItemStack localSlot(int slot) {
        if (slot < 0 || slot >= StorageStackBE.SLOTS) {
            return ItemStack.EMPTY;
        }
        return blockEntity.getItems().getStackInSlot(slot);
    }
}
