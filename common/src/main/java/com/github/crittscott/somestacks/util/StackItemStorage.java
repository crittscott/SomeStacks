package com.github.crittscott.somestacks.util;

import com.mojang.serialization.Dynamic;
import net.minecraft.SharedConstants;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.RegistryOps;
import net.minecraft.util.datafix.DataFixers;
import net.minecraft.util.datafix.fixes.References;
import net.minecraft.world.item.ItemStack;

import javax.annotation.Nonnull;
import java.util.Arrays;

/**
 * A fixed-size, NBT-persisted {@link SlotAccess} implementation. It gives all three loaders the
 * item-handler insert/extract semantics and NBT shape
 * ({@code {Size, Items:[{Slot, ...stack}]}}) used by the block entities.
 *
 * <p>Subclasses override {@link #onContentsChanged(int)}, {@link #isItemValid(int, ItemStack)}, and
 * {@link #getSlotLimit(int)} to specialize notification, admission, and capacity.
 */
public class StackItemStorage implements SlotAccess {
    private static final String TAG_SLOT = "Slot";
    private static final String TAG_SIZE = "Size";
    private static final String TAG_ITEMS = "Items";

    /** Per-slot max-stack-size cap used by the Forge and NeoForge item-handler contracts. */
    private static final int DEFAULT_SLOT_LIMIT = 64;

    /** Data version of Minecraft 1.20.1, the last release before the item Data Components rewrite. */
    private static final int LEGACY_ITEM_DATA_VERSION = 3465;

    private final ItemStack[] stacks;

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
        nbt.putInt(TAG_SIZE, stacks.length);
        return nbt;
    }

    /**
     * Loads slot contents from NBT. A pre-1.21 item tag is upgraded through vanilla's
     * DataFixerUpper before parsing; the return value tells the caller whether any such
     * upgrade happened, so it can mark the owning block entity dirty and persist the rewrite.
     */
    public boolean deserializeNBT(HolderLookup.Provider registries, CompoundTag nbt) {
        Arrays.fill(stacks, ItemStack.EMPTY);
        boolean upgraded = false;
        ListTag list = nbt.getList(TAG_ITEMS, Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag itemTag = list.getCompound(i);
            int slot = itemTag.getInt(TAG_SLOT);
            if (isLegacyItemTag(itemTag)) {
                itemTag = upgradeLegacyItemTag(registries, itemTag);
                itemTag.putInt(TAG_SLOT, slot);
                upgraded = true;
            }
            if (slot >= 0 && slot < stacks.length) {
                stacks[slot] = ItemStack.parse(registries, itemTag).orElse(ItemStack.EMPTY);
            }
        }
        return upgraded;
    }

    /** A pre-1.21 item tag has a byte {@code Count}; the Data Components format has an int {@code count}. */
    private static boolean isLegacyItemTag(CompoundTag itemTag) {
        return !itemTag.contains("count");
    }

    private static CompoundTag upgradeLegacyItemTag(HolderLookup.Provider registries, CompoundTag itemTag) {
        Dynamic<Tag> fixed = DataFixers.getDataFixer().update(
                References.ITEM_STACK,
                new Dynamic<>(RegistryOps.create(NbtOps.INSTANCE, registries), itemTag),
                LEGACY_ITEM_DATA_VERSION,
                SharedConstants.getCurrentVersion().getDataVersion().getVersion());
        return (CompoundTag) fixed.getValue();
    }
}
