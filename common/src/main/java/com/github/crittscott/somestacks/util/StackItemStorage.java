package com.github.crittscott.somestacks.util;

import com.mojang.datafixers.util.Pair;
import com.github.crittscott.somestacks.block.StackDataMigration;
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
import javax.annotation.Nullable;
import java.util.Arrays;
import java.util.Optional;

/**
 * A fixed-size, NBT-persisted {@link SlotAccess} implementation. It gives both loaders the
 * local insert/extract semantics and saved shape
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

    /** Per-slot max-stack-size cap advertised to automation. */
    public static final int STORAGE_SLOT_LIMIT = 64;

    /** Storage capacity after applying the item's own maximum. */
    public static int storageSlotLimit(ItemStack stack) {
        return Math.min(STORAGE_SLOT_LIMIT, stack.getMaxStackSize());
    }

    private final ItemStack[] stacks;
    private final StackDataMigration.SavedItems savedItems = new StackDataMigration.SavedItems();

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
     * {@code stack} itself, so the caller must give up the object or pass a copy.
     */
    public void setStackInSlot(int slot, @Nonnull ItemStack stack) {
        stacks[slot] = stack;
        onContentsChanged(slot);
    }

    /** Inserts up to the legal capacity, returning the remainder; an accepted stack may be retained. */
    @Nonnull
    public ItemStack insertItem(int slot, @Nonnull ItemStack stack) {
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

        if (existing.isEmpty()) {
            stacks[slot] = reachedLimit ? stack.copyWithCount(limit) : stack;
        } else {
            existing.grow(reachedLimit ? limit : stack.getCount());
        }
        onContentsChanged(slot);

        return reachedLimit ? stack.copyWithCount(stack.getCount() - limit) : ItemStack.EMPTY;
    }

    /** Removes up to the requested amount and returns an owned stack, notifying on mutation. */
    @Nonnull
    public ItemStack extractItem(int slot, int amount) {
        if (amount == 0) {
            return ItemStack.EMPTY;
        }

        ItemStack existing = stacks[slot];
        if (existing.isEmpty()) {
            return ItemStack.EMPTY;
        }

        int toExtract = Math.min(amount, existing.getMaxStackSize());

        if (existing.getCount() <= toExtract) {
            stacks[slot] = ItemStack.EMPTY;
            onContentsChanged(slot);
            return existing;
        }
        stacks[slot] = existing.copyWithCount(existing.getCount() - toExtract);
        onContentsChanged(slot);
        return existing.copyWithCount(toExtract);
    }

    /** Per-position admission capacity, before the item's own maximum. */
    protected int getSlotLimit(int slot) {
        return STORAGE_SLOT_LIMIT;
    }

    /** Admission for a newly offered item; internal relocation bypasses it. */
    protected boolean isItemValid(int slot, @Nonnull ItemStack stack) {
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
                return CompoundTag.CODEC.encode(storage.save(ops), ops, prefix);
            }

            @Override
            public <T> DataResult<Pair<StackItemStorage, T>> decode(DynamicOps<T> ops, T input) {
                return CompoundTag.CODEC.decode(ops, input).map(pair -> pair.mapFirst(tag -> {
                    StackItemStorage storage = new StackItemStorage(slots);
                    storage.load(ops, tag);
                    return storage;
                }));
            }
        };
    }

    public CompoundTag save(HolderLookup.Provider registries) {
        return save(RegistryOps.create(NbtOps.INSTANCE, registries));
    }

    private <T> CompoundTag save(DynamicOps<T> ops) {
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
        savedItems.saveTo(nbt);
        return nbt;
    }

    /**
     * Loads slot contents from NBT, setting aside every item tag that cannot be placed. The tag must
     * already be in the running game's format. Ordinary entries take precedence over retries;
     * loading bypasses admission and does not call contents-change hooks. The caller reports
     * returned retention counts once it knows the world location.
     */
    public StackDataMigration.LoadResult load(HolderLookup.Provider registries, CompoundTag nbt) {
        return load(RegistryOps.create(NbtOps.INSTANCE, registries), nbt);
    }

    private <T> StackDataMigration.LoadResult load(DynamicOps<T> ops, CompoundTag nbt) {
        return savedItems.load(ops, nbt, stacks);
    }

    /** Checks a wire inventory without changing live slots or retained disk entries. */
    public boolean isValidClientUpdate(HolderLookup.Provider registries, CompoundTag nbt) {
        return decodeClientUpdate(registries, nbt) != null;
    }

    /** Validates a current-format client snapshot before replacing any local contents. */
    public boolean deserializeClientUpdate(HolderLookup.Provider registries, CompoundTag nbt) {
        ItemStack[] decoded = decodeClientUpdate(registries, nbt);
        if (decoded == null) return false;
        System.arraycopy(decoded, 0, stacks, 0, stacks.length);
        savedItems.clear();
        return true;
    }

    @Nullable
    private ItemStack[] decodeClientUpdate(HolderLookup.Provider registries, CompoundTag nbt) {
        if (nbt.contains(TAG_SET_ASIDE) || !(nbt.get(TAG_ITEMS) instanceof ListTag list)
                || list.size() > stacks.length) {
            return null;
        }
        ItemStack[] decoded = new ItemStack[stacks.length];
        Arrays.fill(decoded, ItemStack.EMPTY);
        boolean[] used = new boolean[stacks.length];
        RegistryOps<Tag> ops = RegistryOps.create(NbtOps.INSTANCE, registries);
        for (int i = 0; i < list.size(); i++) {
            if (!(list.get(i) instanceof CompoundTag entry)
                    || !(entry.get(TAG_SLOT) instanceof IntTag slotTag)) return null;
            int slot = slotTag.intValue();
            if (slot < 0 || slot >= stacks.length || used[slot]) return null;
            Optional<ItemStack> parsed = ItemStack.CODEC.parse(ops, entry).result();
            if (parsed.isEmpty() || parsed.get().isEmpty()) return null;
            used[slot] = true;
            decoded[slot] = parsed.get();
        }
        return decoded;
    }

    /** Removes the set-aside tags from serialized storage, which clients have no use for. */
    public static void stripSetAside(CompoundTag nbt) {
        nbt.remove(TAG_SET_ASIDE);
    }
}
