package com.github.crittscott.somestacks.util;

import com.github.crittscott.somestacks.SomeStacksCommon;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;

import javax.annotation.Nonnull;
import java.util.Arrays;
import java.util.Optional;

/**
 * A fixed-size, NBT-persisted {@link SlotAccess} implementation. It gives all three loaders the
 * item-handler insert/extract semantics and NBT shape
 * ({@code {Items:[{Slot, ...stack}], SetAside:[...]}}) used by the block entities.
 *
 * <p>A saved item tag that cannot be read, or whose slot is out of range or already taken, is set
 * aside rather than dropped: it is kept verbatim under {@code SetAside}, saved back with the
 * block, and retried on every load, so contents from a temporarily missing mod return when it does.
 *
 * <p>Subclasses override {@link #onContentsChanged(int)}, {@link #isItemValid(int, ItemStack)}, and
 * {@link #getSlotLimit(int)} to specialize notification, admission, and capacity.
 */
public class StackItemStorage implements SlotAccess {
    public static final String TAG_SLOT = "Slot";
    public static final String TAG_ITEMS = "Items";
    public static final String TAG_SET_ASIDE = "SetAside";

    /** Per-slot max-stack-size cap used by the Forge and NeoForge item-handler contracts. */
    private static final int DEFAULT_SLOT_LIMIT = 64;

    private final ItemStack[] stacks;
    private final ListTag setAside = new ListTag();

    public StackItemStorage(int size) {
        stacks = new ItemStack[size];
        Arrays.fill(stacks, ItemStack.EMPTY);
    }

    @Override
    public int getSlots() {
        return stacks.length;
    }

    @Nonnull
    @Override
    public ItemStack getStackInSlot(int slot) {
        return stacks[slot];
    }

    /**
     * Replaces a slot directly and reports the change, bypassing admission and capacity checks.
     * Internal relocation and settlement use this only for already-stored contents.
     */
    public void setStackInSlot(int slot, @Nonnull ItemStack stack) {
        stacks[slot] = stack;
        onContentsChanged(slot);
    }

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
            if (!ItemStack.isSameItemSameComponents(existing, stack) || !existing.isStackable()) {
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

    public CompoundTag serializeNBT(HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        for (int i = 0; i < stacks.length; i++) {
            if (!stacks[i].isEmpty()) {
                CompoundTag itemTag = new CompoundTag();
                itemTag.putInt(TAG_SLOT, i);
                list.add(stacks[i].save(registries, itemTag));
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
     * Loads slot contents from NBT, setting aside every item tag that cannot be placed. The tag must
     * already be in the running game's format; {@code owner} names the block in the log.
     */
    public void deserializeNBT(HolderLookup.Provider registries, CompoundTag nbt, BlockPos owner) {
        Arrays.fill(stacks, ItemStack.EMPTY);
        setAside.clear();
        readItems(registries, nbt.getList(TAG_ITEMS, Tag.TAG_COMPOUND));
        readItems(registries, nbt.getList(TAG_SET_ASIDE, Tag.TAG_COMPOUND));
        if (!setAside.isEmpty()) {
            SomeStacksCommon.LOGGER.warn("Kept {} unreadable saved item stack(s) aside in the stack block at {}",
                    setAside.size(), owner.toShortString());
        }
    }

    private void readItems(HolderLookup.Provider registries, ListTag list) {
        for (int i = 0; i < list.size(); i++) {
            CompoundTag itemTag = list.getCompound(i);
            int slot = itemTag.getInt(TAG_SLOT);
            Optional<ItemStack> parsed = slot >= 0 && slot < stacks.length && stacks[slot].isEmpty()
                    ? ItemStack.parse(registries, itemTag)
                    : Optional.empty();
            if (parsed.isPresent()) {
                stacks[slot] = parsed.get();
            } else {
                setAside.add(itemTag.copy());
            }
        }
    }

    /** Removes the set-aside tags from serialized storage, which clients have no use for. */
    public static void stripSetAside(CompoundTag nbt) {
        nbt.remove(TAG_SET_ASIDE);
    }
}
