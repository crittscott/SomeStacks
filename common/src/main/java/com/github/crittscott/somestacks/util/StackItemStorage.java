package com.github.crittscott.somestacks.util;

import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.DynamicOps;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.resources.RegistryOps;
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

    /** Counts of newly retained entries and persisted entries that still could not be restored. */
    public record LoadResult(int newlySetAside, int failedRetries) {}

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
     * Internal relocation and settlement use this only for already-stored contents. The slot keeps
     * {@code stack} itself, so the caller must give it up or pass a copy.
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
                stacks[slot] = reachedLimit ? stack.copyWithCount(limit) : stack;
            } else {
                existing.grow(reachedLimit ? limit : stack.getCount());
            }
            onContentsChanged(slot);
        }

        return reachedLimit ? stack.copyWithCount(stack.getCount() - limit) : ItemStack.EMPTY;
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
            stacks[slot] = existing.copyWithCount(existing.getCount() - toExtract);
            onContentsChanged(slot);
        }
        return existing.copyWithCount(toExtract);
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

    /** Uses the value output's registry context for item components, retaining the disk envelope. */
    public static Codec<StackItemStorage> codec(int slots) {
        return new Codec<>() {
            @Override
            public <T> DataResult<T> encode(StackItemStorage storage, DynamicOps<T> ops, T prefix) {
                return CompoundTag.CODEC.encode(storage.serializeNBT(ops), ops, prefix);
            }

            @Override
            public <T> DataResult<Pair<StackItemStorage, T>> decode(DynamicOps<T> ops, T input) {
                return CompoundTag.CODEC.decode(ops, input).map(pair -> pair.mapFirst(tag -> {
                    StackItemStorage storage = new StackItemStorage(slots);
                    storage.deserializeNBT(ops, tag);
                    return storage;
                }));
            }
        };
    }

    public CompoundTag serializeNBT(HolderLookup.Provider registries) {
        return serializeNBT(RegistryOps.create(NbtOps.INSTANCE, registries));
    }

    private <T> CompoundTag serializeNBT(DynamicOps<T> ops) {
        ListTag list = new ListTag();
        for (int i = 0; i < stacks.length; i++) {
            if (!stacks[i].isEmpty()) {
                // Never publish a partial inventory when a component cannot be encoded.
                T encoded;
                try {
                    encoded = ItemStack.CODEC.encodeStart(ops, stacks[i]).getOrThrow();
                } catch (RuntimeException failure) {
                    throw new IllegalStateException("Cannot encode stored item in slot " + i, failure);
                }
                Tag raw = ops.convertTo(NbtOps.INSTANCE, encoded);
                if (!(raw instanceof CompoundTag itemTag)) {
                    throw new IllegalStateException("Item codec did not produce a compound for slot " + i);
                }
                itemTag.putInt(TAG_SLOT, i);
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
     * Loads slot contents from NBT, setting aside every item tag that cannot be placed. The tag must
     * already be in the running game's format. Ordinary entries take precedence over retries;
     * loading bypasses admission and does not call contents-change hooks. The caller reports
     * returned retention counts once it knows the world location.
     */
    public LoadResult deserializeNBT(HolderLookup.Provider registries, CompoundTag nbt) {
        return deserializeNBT(RegistryOps.create(NbtOps.INSTANCE, registries), nbt);
    }

    private <T> LoadResult deserializeNBT(DynamicOps<T> ops, CompoundTag nbt) {
        Arrays.fill(stacks, ItemStack.EMPTY);
        setAside.clear();
        readItems(ops, nbt.getListOrEmpty(TAG_ITEMS));
        int newlySetAside = setAside.size();
        readItems(ops, nbt.getListOrEmpty(TAG_SET_ASIDE));
        return new LoadResult(newlySetAside, setAside.size() - newlySetAside);
    }

    private <T> void readItems(DynamicOps<T> ops, ListTag list) {
        for (Tag entry : list) {
            if (!(entry instanceof CompoundTag itemTag) || !(itemTag.get(TAG_SLOT) instanceof IntTag slotTag)) {
                setAside.add(entry.copy());
                continue;
            }
            int slot = slotTag.intValue();
            Optional<ItemStack> parsed = slot >= 0 && slot < stacks.length && stacks[slot].isEmpty()
                    ? ItemStack.CODEC.parse(ops, NbtOps.INSTANCE.convertTo(ops, itemTag)).result()
                    : Optional.empty();
            if (parsed.isPresent() && !parsed.get().isEmpty()) {
                stacks[slot] = parsed.get();
            } else {
                setAside.add(itemTag.copy());
            }
        }
    }

    /** Validates a current-format client snapshot before replacing any local contents. */
    public boolean deserializeClientUpdate(HolderLookup.Provider registries, CompoundTag nbt) {
        if (nbt.contains(TAG_SET_ASIDE) || !(nbt.get(TAG_ITEMS) instanceof ListTag list)
                || list.size() > stacks.length) {
            return false;
        }
        ItemStack[] decoded = new ItemStack[stacks.length];
        Arrays.fill(decoded, ItemStack.EMPTY);
        boolean[] used = new boolean[stacks.length];
        RegistryOps<Tag> ops = RegistryOps.create(NbtOps.INSTANCE, registries);
        for (int i = 0; i < list.size(); i++) {
            if (!(list.get(i) instanceof CompoundTag entry)
                    || !(entry.get(TAG_SLOT) instanceof IntTag slotTag)) return false;
            int slot = slotTag.intValue();
            if (slot < 0 || slot >= stacks.length || used[slot]) return false;
            Optional<ItemStack> parsed = ItemStack.CODEC.parse(ops, entry).result();
            if (parsed.isEmpty() || parsed.get().isEmpty()) return false;
            used[slot] = true;
            decoded[slot] = parsed.get();
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
