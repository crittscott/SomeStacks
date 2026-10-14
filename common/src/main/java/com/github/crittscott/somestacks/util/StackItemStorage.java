package com.github.crittscott.somestacks.util;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;

import javax.annotation.Nonnull;
import java.util.Arrays;

/**
 * A fixed-size, NBT-persisted {@link SlotAccess} implementation. Loader-neutral replacement for
 * Forge's {@code ItemStackHandler}, matching its insert/extract semantics and its NBT shape
 * ({@code {Size, Items:[{Slot, ...stack}]}}) exactly, so existing world saves keep loading.
 *
 * <p>Subclasses override {@link #onContentsChanged(int)}, {@link #isItemValid(int, ItemStack)}, and
 * {@link #getSlotLimit(int)} the way a Forge {@code ItemStackHandler} anonymous subclass would.
 */
public class StackItemStorage implements SlotAccess {
    private static final String TAG_SLOT = "Slot";
    private static final String TAG_SIZE = "Size";
    private static final String TAG_ITEMS = "Items";

    /** Per-slot max-stack-size cap, mirroring Forge {@code ItemStackHandler}'s default. */
    private static final int DEFAULT_SLOT_LIMIT = 64;

    private final ItemStack[] stacks;

    /**
     * Creates an empty inventory with this fixed slot count. Loading NBT never resizes it.
     *
     * @param size nonnegative number of slots
     */
    public StackItemStorage(int size) {
        stacks = new ItemStack[size];
        Arrays.fill(stacks, ItemStack.EMPTY);
    }

    @Override
    public int getSlots() {
        return stacks.length;
    }

    /**
     * Returns the stored stack itself, without copying it. Callers must not mutate this reference
     * to bypass notifications; use storage mutations or {@link #setStackInSlot(int, ItemStack)}.
     */
    @Nonnull
    @Override
    public ItemStack getStackInSlot(int slot) {
        return stacks[slot];
    }

    /**
     * Stores the supplied stack reference without copying or checking admission or stack limits,
     * then calls {@link #onContentsChanged(int)}. The caller transfers ownership and must not mutate
     * the stack afterward. Slot indices must be within this inventory's fixed capacity.
     */
    public void setStackInSlot(int slot, @Nonnull ItemStack stack) {
        stacks[slot] = stack;
        onContentsChanged(slot);
    }

    /**
     * {@inheritDoc}
     *
     * <p>A full insertion into an empty slot can retain the supplied stack reference. Callers
     * must relinquish an accepted offer or supply a copy before further mutation. Successful
     * committed insertions notify {@link #onContentsChanged(int)}; simulations do not.
     */
    @Nonnull
    @Override
    public ItemStack insertItem(int slot, @Nonnull ItemStack stack, boolean simulate) {
        if (stack.isEmpty()) {
            return ItemStack.EMPTY;
        }
        if (!isItemValid(slot, stack)) {
            return stack;
        }

        ItemStack existing = stacks[slot];
        int limit = Math.min(getSlotLimit(slot), stack.getMaxStackSize());

        if (!existing.isEmpty()) {
            if (!ItemStack.isSameItemSameTags(existing, stack) || !existing.isStackable()) {
                return stack;
            }
            limit -= existing.getCount();
        }

        if (limit <= 0) {
            return stack;
        }

        boolean reachedLimit = stack.getCount() > limit;

        if (!simulate) {
            if (existing.isEmpty()) {
                stacks[slot] = reachedLimit ? copyWithSize(stack, limit) : stack;
            } else {
                existing.grow(reachedLimit ? limit : stack.getCount());
            }
            onContentsChanged(slot);
        }

        return reachedLimit ? copyWithSize(stack, stack.getCount() - limit) : ItemStack.EMPTY;
    }

    @Nonnull
    @Override
    public ItemStack extractItem(int slot, int amount, boolean simulate) {
        if (amount == 0) {
            return ItemStack.EMPTY;
        }

        ItemStack existing = stacks[slot];
        if (existing.isEmpty()) {
            return ItemStack.EMPTY;
        }

        int toExtract = Math.min(amount, existing.getMaxStackSize());

        if (existing.getCount() <= toExtract) {
            if (!simulate) {
                stacks[slot] = ItemStack.EMPTY;
                onContentsChanged(slot);
                return existing;
            }
            return existing.copy();
        }

        if (!simulate) {
            stacks[slot] = copyWithSize(existing, existing.getCount() - toExtract);
            onContentsChanged(slot);
        }
        return copyWithSize(existing, toExtract);
    }

    @Override
    public int getSlotLimit(int slot) {
        return DEFAULT_SLOT_LIMIT;
    }

    @Override
    public boolean isItemValid(int slot, @Nonnull ItemStack stack) {
        return true;
    }

    /** Called after a slot's contents change, including a direct {@link #setStackInSlot}. */
    protected void onContentsChanged(int slot) {
    }

    private static ItemStack copyWithSize(ItemStack stack, int size) {
        if (size <= 0) {
            return ItemStack.EMPTY;
        }
        ItemStack copy = stack.copy();
        copy.setCount(size);
        return copy;
    }

    /**
     * Creates an NBT snapshot containing {@code Size} and nonempty {@code Items} entries with
     * local slot indices. Does not mutate the inventory or call change notifications.
     */
    public CompoundTag serializeNBT() {
        ListTag list = new ListTag();
        for (int i = 0; i < stacks.length; i++) {
            if (!stacks[i].isEmpty()) {
                CompoundTag itemTag = new CompoundTag();
                itemTag.putInt(TAG_SLOT, i);
                stacks[i].save(itemTag);
                list.add(itemTag);
            }
        }
        CompoundTag nbt = new CompoundTag();
        nbt.put(TAG_ITEMS, list);
        nbt.putInt(TAG_SIZE, stacks.length);
        return nbt;
    }

    /**
     * Clears and replaces contents from {@code Items}, ignoring {@code Size} and out-of-range
     * slot entries. Does not check admission or call {@link #onContentsChanged(int)}. Callers must
     * refresh their derived fill, shapes, and run caches and arrange publication as needed.
     */
    public void deserializeNBT(CompoundTag nbt) {
        Arrays.fill(stacks, ItemStack.EMPTY);
        ListTag list = nbt.getList(TAG_ITEMS, Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag itemTag = list.getCompound(i);
            int slot = itemTag.getInt(TAG_SLOT);
            if (slot >= 0 && slot < stacks.length) {
                stacks[slot] = ItemStack.of(itemTag);
            }
        }
    }
}
