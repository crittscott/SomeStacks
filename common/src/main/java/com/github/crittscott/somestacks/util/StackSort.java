package com.github.crittscott.somestacks.util;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import java.util.Comparator;

/** Comparator used to lay out the contents of a settled Storage pile. */
public final class StackSort {
    private StackSort(){}

    /**
     * Orders stacks by item id, damage, tags, and descending count, with empty stacks last. This
     * gives identical piles the same layout and leaves a group's partial stack at its end.
     */
    public static final Comparator<ItemStack> COMPARATOR = (a, b) -> {
        if (a.isEmpty() && b.isEmpty()) return 0;
        if (a.isEmpty()) return 1;
        if (b.isEmpty()) return -1;

        int c = key(a).compareTo(key(b));
        if (c != 0) return c;

        // Fullest first leaves a group's partial stack at its end.
        return Integer.compare(b.getCount(), a.getCount());
    };

    /** Computes the expensive sortable fields once for an exact settlement identity. */
    public static SortKey key(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        return new SortKey(BuiltInRegistries.ITEM.getKey(stack.getItem()), stack.getDamageValue(),
                tag == null ? null : tag.toString());
    }

    public record SortKey(ResourceLocation itemId, int damage, String tag) implements Comparable<SortKey> {
        @Override
        public int compareTo(SortKey other) {
            int order = itemId.compareTo(other.itemId);
            if (order != 0) return order;
            order = Integer.compare(damage, other.damage);
            if (order != 0) return order;
            if (tag == null) return other.tag == null ? 0 : -1;
            if (other.tag == null) return 1;
            return tag.compareTo(other.tag);
        }
    }
}
