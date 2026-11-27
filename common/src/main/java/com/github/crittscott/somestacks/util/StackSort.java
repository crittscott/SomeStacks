package com.github.crittscott.somestacks.util;

import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import javax.annotation.Nullable;

/** Sort key that orders the contents of a settled Storage pile. */
public final class StackSort {
    private StackSort() {}

    /**
     * The identity's sortable fields: item id, then damage, then untagged before component-bearing,
     * then the component patch's text. The patch is immutable, so the key stays valid whatever
     * later happens to the stack.
     */
    public static SortKey key(ItemStack stack) {
        return new SortKey(
                BuiltInRegistries.ITEM.getKey(stack.getItem()),
                stack.getDamageValue(),
                stack.getComponentsPatch());
    }

    /**
     * Sort fields for one identity. The patch's text is computed only when two keys tie on item
     * and damage, and at most once per key, since most piles never compare by components.
     */
    public static final class SortKey implements Comparable<SortKey> {
        private final int damage;
        private final ResourceLocation itemId;
        private final DataComponentPatch patch;
        @Nullable private String componentText;

        private SortKey(ResourceLocation itemId, int damage, DataComponentPatch patch) {
            this.itemId = itemId;
            this.damage = damage;
            this.patch = patch;
        }

        @Override
        public int compareTo(SortKey other) {
            int order = itemId.compareTo(other.itemId);
            if (order != 0) return order;
            order = Integer.compare(damage, other.damage);
            if (order != 0) return order;
            if (patch.isEmpty()) return other.patch.isEmpty() ? 0 : -1;
            if (other.patch.isEmpty()) return 1;
            return componentText().compareTo(other.componentText());
        }

        private String componentText() {
            if (componentText == null) componentText = patch.toString();
            return componentText;
        }
    }
}
