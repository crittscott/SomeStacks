package com.github.crittscott.somestacks.util;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import javax.annotation.Nullable;

/** Ordering used to lay out the contents of a settled Storage pile. */
public final class StackSort {
    private StackSort() {}

    /**
     * Orders nonempty pieces by cached item id, damage, tags, and descending count. This gives
     * identical piles the same layout and leaves a group's partial stack at its end.
     */
    public static int comparePieces(SortKey a, int aCount, SortKey b, int bCount) {
        int c = a.compareTo(b);
        if (c != 0) return c;

        // Fullest first leaves a group's partial stack at its end.
        return Integer.compare(bCount, aCount);
    }

    /**
     * The sort key of an exact settlement identity. It reads {@code stack}'s tag when a comparison
     * needs it, so it is valid only while that tag is unchanged.
     */
    public static SortKey key(ItemStack stack) {
        return new SortKey(
                BuiltInRegistries.ITEM.getKey(stack.getItem()),
                stack.getDamageValue(),
                stack.getTag());
    }

    /**
     * Sort fields of one settlement identity: item id, then damage, then tag text. The tag text is
     * a full SNBT serialization, so it is computed only when two keys tie on item and damage, and
     * at most once per key.
     */
    public static final class SortKey implements Comparable<SortKey> {
        private final int damage;
        private final ResourceLocation itemId;
        @Nullable private final CompoundTag tag;
        @Nullable private String tagText;

        private SortKey(ResourceLocation itemId, int damage, @Nullable CompoundTag tag) {
            this.itemId = itemId;
            this.damage = damage;
            this.tag = tag;
        }

        @Override
        public int compareTo(SortKey other) {
            int order = itemId.compareTo(other.itemId);
            if (order != 0) return order;
            order = Integer.compare(damage, other.damage);
            if (order != 0) return order;
            if (tag == null) return other.tag == null ? 0 : -1;
            if (other.tag == null) return 1;
            return tagText().compareTo(other.tagText());
        }

        private String tagText() {
            if (tagText == null) {
                tagText = tag.toString();
            }
            return tagText;
        }
    }
}
