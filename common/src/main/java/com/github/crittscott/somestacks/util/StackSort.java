package com.github.crittscott.somestacks.util;

import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;


/** Comparator used to lay out the contents of a settled Storage pile. */
public final class StackSort {
    private StackSort(){}

    /** Computes the identity's sortable fields once for a settlement group. */
    public static SortKey key(ItemStack stack) {
        DataComponentPatch patch = stack.getComponentsPatch();
        return new SortKey(BuiltInRegistries.ITEM.getKey(stack.getItem()), stack.getDamageValue(),
                patch.isEmpty() ? null : patch.toString());
    }

    public record SortKey(ResourceLocation itemId, int damage, String components)
            implements Comparable<SortKey> {
        @Override
        public int compareTo(SortKey other) {
            int order = itemId.compareTo(other.itemId);
            if (order != 0) return order;
            order = Integer.compare(damage, other.damage);
            if (order != 0) return order;
            if (components == null) return other.components == null ? 0 : -1;
            if (other.components == null) return 1;
            return components.compareTo(other.components);
        }
    }
}
