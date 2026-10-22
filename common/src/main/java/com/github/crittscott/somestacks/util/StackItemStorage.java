package com.github.crittscott.somestacks.util;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Arrays;

/**
 * Fixed-size block-local storage with simulated insertion/extraction and NBT persistence.
 * Automation addresses complete runs through the loader adapters, rather than exposing this inventory.
 *
 * <p>Unreadable, out-of-range, or duplicate-slot item tags are retained verbatim under
 * {@code SetAside}, saved with the block, and retried on each load. Loading and direct assignment
 * bypass admission so stored items remain recoverable after policy changes.
 *
 * <p>Subclasses supply slot limits, admission, and content-change notifications.
 */
public class StackItemStorage {
    public static final String TAG_SLOT = "Slot";
    public static final String TAG_ITEMS = "Items";
    public static final String TAG_SET_ASIDE = "SetAside";
    private static final String TAG_ID = "id";

    /** Default per-slot capacity before the item's own maximum applies. */
    public static final int DEFAULT_SLOT_LIMIT = 64;

    private final ItemStack[] stacks;
    private final ListTag setAside = new ListTag();

    /** Counts of newly retained entries and persisted entries that still could not be restored. */
    public record LoadResult(int newlySetAside, int failedRetries) {}

    /**
     * Creates an empty inventory with this fixed slot count. Loading NBT never resizes it.
     *
     * @param size nonnegative number of slots
     */
    public StackItemStorage(int size) {
        stacks = new ItemStack[size];
        Arrays.fill(stacks, ItemStack.EMPTY);
    }

    /** Number of local slots, indexed from zero. */
    public int getSlots() {
        return stacks.length;
    }

    /**
     * Returns the stored stack itself, without copying it. Callers must not mutate this reference
     * to bypass notifications; use storage mutations or {@link #setStackInSlot(int, ItemStack)}.
     */
    @Nonnull
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
     * Inserts into one slot and returns the unaccepted remainder without mutating the offer.
     * Simulation computes the remainder without changing contents.
     *
     * <p>A full insertion into an empty slot can retain the supplied stack reference. Callers
     * must relinquish an accepted offer or supply a copy before further mutation. Successful
     * committed insertions notify {@link #onContentsChanged(int)}; simulations do not.
     */
    @Nonnull
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

    /** Removes up to the requested amount from one slot; simulation leaves contents unchanged. */
    @Nonnull
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

    /** Capacity of one local slot before the item's own maximum applies. */
    public int getSlotLimit(int slot) {
        return DEFAULT_SLOT_LIMIT;
    }

    /** New-item admission for one local slot, independent of capacity. */
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
     * Creates an NBT snapshot containing nonempty {@code Items} entries with local slot indices and
     * any set-aside tags. Does not mutate the inventory or call change notifications.
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
        if (!setAside.isEmpty()) {
            nbt.put(TAG_SET_ASIDE, setAside.copy());
        }
        return nbt;
    }

    /**
     * Loads slot contents from NBT, setting aside every item tag that cannot be placed. Ordinary
     * entries take precedence over retries; loading bypasses admission and does not call
     * {@link #onContentsChanged(int)}. The caller reports returned retention counts once it knows the
     * world location, and refreshes derived fill, shapes, and run caches.
     */
    public LoadResult deserializeNBT(CompoundTag nbt) {
        Arrays.fill(stacks, ItemStack.EMPTY);
        setAside.clear();
        readItems(nbt.getList(TAG_ITEMS, Tag.TAG_COMPOUND));
        int newlySetAside = setAside.size();
        readItems(nbt.getList(TAG_SET_ASIDE, Tag.TAG_COMPOUND));
        return new LoadResult(newlySetAside, setAside.size() - newlySetAside);
    }

    private void readItems(ListTag list) {
        for (int i = 0; i < list.size(); i++) {
            CompoundTag itemTag = list.getCompound(i);
            int slot = itemTag.getInt(TAG_SLOT);
            ItemStack parsed = slot >= 0 && slot < stacks.length && stacks[slot].isEmpty()
                    ? parse(itemTag)
                    : null;
            if (parsed != null) {
                stacks[slot] = parsed;
            } else {
                setAside.add(itemTag.copy());
            }
        }
    }

    /**
     * Reads one saved stack, or returns null when its id is malformed or names no registered item.
     * {@link ItemStack#of} reads both cases as an empty stack, which would lose the entry.
     */
    @Nullable
    private static ItemStack parse(CompoundTag itemTag) {
        ResourceLocation id = ResourceLocation.tryParse(itemTag.getString(TAG_ID));
        if (id == null || !BuiltInRegistries.ITEM.containsKey(id)) {
            return null;
        }
        ItemStack stack = ItemStack.of(itemTag);
        return stack.isEmpty() ? null : stack;
    }

    /** Validates a client snapshot completely before replacing any local contents. */
    public boolean deserializeClientUpdate(CompoundTag nbt) {
        if (nbt.contains(TAG_SET_ASIDE) || !(nbt.get(TAG_ITEMS) instanceof ListTag list)
                || list.size() > stacks.length
                || (!list.isEmpty() && list.getElementType() != Tag.TAG_COMPOUND)) {
            return false;
        }
        ItemStack[] decoded = new ItemStack[stacks.length];
        Arrays.fill(decoded, ItemStack.EMPTY);
        boolean[] used = new boolean[stacks.length];
        for (int i = 0; i < list.size(); i++) {
            CompoundTag entry = list.getCompound(i);
            if (!entry.contains(TAG_SLOT, Tag.TAG_INT)) return false;
            int slot = entry.getInt(TAG_SLOT);
            if (slot < 0 || slot >= stacks.length || used[slot]) return false;
            ItemStack parsed = parse(entry);
            if (parsed == null) return false;
            used[slot] = true;
            decoded[slot] = parsed;
        }
        System.arraycopy(decoded, 0, stacks, 0, stacks.length);
        setAside.clear();
        return true;
    }

    /** Removes the set-aside tags from serialized storage, which clients have no use for. */
    public static void stripSetAside(CompoundTag nbt) {
        nbt.remove(TAG_SET_ASIDE);
    }
}
