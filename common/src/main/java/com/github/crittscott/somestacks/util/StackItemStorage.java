package com.github.crittscott.somestacks.util;

import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.RegistryOps;
import net.minecraft.world.item.ItemStack;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.function.UnaryOperator;

import javax.annotation.Nonnull;

/**
 * A fixed-size, NBT-persisted {@link SlotAccess} implementation. It gives all three loaders the
 * item-handler insert/extract semantics and NBT shape ({@code {Items:[{Slot, ...stack}],
 * SetAside:[...]}}) used by the block entities.
 *
 * <p>A saved item tag that cannot be read, or whose slot is out of range or already taken, is set
 * aside rather than dropped: it is kept verbatim under {@code SetAside}, saved back with the block,
 * and retried on every load, so contents from a temporarily missing mod return when it does.
 *
 * <p>Subclasses override {@link #onContentsChanged(int)}, {@link #isItemValid(int, ItemStack)}, and
 * {@link #getSlotLimit(int)} to specialize notification, admission, and capacity.
 */
public class StackItemStorage implements SlotAccess {
    public static final String TAG_SLOT = "Slot";
    public static final String TAG_ITEMS = "Items";
    public static final String TAG_SET_ASIDE = "SetAside";

    /**
     * Item data no renderer draws that can be large: the contents of container items and book
     * pages. Client updates leave it out, as a chest sends nearby players none of its contents.
     */
    private static final List<DataComponentType<?>> UNDRAWN_COMPONENTS =
            List.of(
                    DataComponents.CONTAINER,
                    DataComponents.WRITABLE_BOOK_CONTENT,
                    DataComponents.WRITTEN_BOOK_CONTENT);

    /** Per-slot capacity before the item's own stack limit, unless a subclass narrows it. */
    public static final int DEFAULT_SLOT_LIMIT = 64;

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
    protected void onContentsChanged(int slot) {}

    /** The saved form: every nonempty slot, plus {@code SetAside} when anything is set aside. */
    public CompoundTag serializeNBT(HolderLookup.Provider registries) {
        CompoundTag nbt = serializeSlots(registries, UnaryOperator.identity());
        if (!setAside.isEmpty()) {
            nbt.put(TAG_SET_ASIDE, setAside.copy());
        }
        return nbt;
    }

    /**
     * The client update form: every nonempty slot without its {@code UNDRAWN_COMPONENTS}, and no
     * set-aside data, which clients have no use for. Stored stacks are not changed.
     */
    public CompoundTag serializeClientUpdate(HolderLookup.Provider registries) {
        return serializeSlots(registries, StackItemStorage::withoutUndrawnData);
    }

    private CompoundTag serializeSlots(
            HolderLookup.Provider registries, UnaryOperator<ItemStack> view) {
        ListTag list = new ListTag();
        for (int i = 0; i < stacks.length; i++) {
            if (!stacks[i].isEmpty()) {
                CompoundTag itemTag = new CompoundTag();
                itemTag.putInt(TAG_SLOT, i);
                list.add(view.apply(stacks[i]).save(registries, itemTag));
            }
        }
        CompoundTag nbt = new CompoundTag();
        nbt.put(TAG_ITEMS, list);
        return nbt;
    }

    private static ItemStack withoutUndrawnData(ItemStack stack) {
        ItemStack view = stack;
        for (DataComponentType<?> type : UNDRAWN_COMPONENTS) {
            if (view.has(type)) {
                if (view == stack) {
                    view = stack.copy();
                }
                view.remove(type);
            }
        }
        return view;
    }

    /**
     * Loads slot contents from NBT, setting aside every item tag that cannot be placed. The tag
     * must already be in the running game's format. Ordinary entries take precedence over retries;
     * loading bypasses admission and does not call contents-change hooks. The caller reports
     * returned retention counts once it knows the world location.
     */
    public LoadResult deserializeNBT(HolderLookup.Provider registries, CompoundTag nbt) {
        Arrays.fill(stacks, ItemStack.EMPTY);
        setAside.clear();
        readItems(registries, nbt.getList(TAG_ITEMS, Tag.TAG_COMPOUND));
        int newlySetAside = setAside.size();
        readItems(registries, nbt.getList(TAG_SET_ASIDE, Tag.TAG_COMPOUND));
        return new LoadResult(newlySetAside, setAside.size() - newlySetAside);
    }

    private void readItems(HolderLookup.Provider registries, ListTag list) {
        for (int i = 0; i < list.size(); i++) {
            CompoundTag itemTag = list.getCompound(i);
            int slot = itemTag.getInt(TAG_SLOT);
            Optional<ItemStack> parsed =
                    slot >= 0 && slot < stacks.length && stacks[slot].isEmpty()
                            ? ItemStack.parse(registries, itemTag)
                            : Optional.empty();
            if (parsed.isPresent()) {
                stacks[slot] = parsed.get();
            } else {
                setAside.add(itemTag.copy());
            }
        }
    }

    /** Validates a current-format client snapshot before replacing any local contents. */
    public boolean deserializeClientUpdate(HolderLookup.Provider registries, CompoundTag nbt) {
        if (nbt.contains(TAG_SET_ASIDE)
                || !(nbt.get(TAG_ITEMS) instanceof ListTag list)
                || list.size() > stacks.length
                || (!list.isEmpty() && list.getElementType() != Tag.TAG_COMPOUND)) {
            return false;
        }
        ItemStack[] decoded = new ItemStack[stacks.length];
        Arrays.fill(decoded, ItemStack.EMPTY);
        boolean[] used = new boolean[stacks.length];
        RegistryOps<Tag> ops = RegistryOps.create(NbtOps.INSTANCE, registries);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag entry = list.getCompound(i);
            if (!entry.contains(TAG_SLOT, Tag.TAG_INT)) return false;
            int slot = entry.getInt(TAG_SLOT);
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
}
