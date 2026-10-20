package com.github.crittscott.somestacks.util;

import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

/** Comparator used to lay out the contents of a settled Storage pile. */
public final class StackSort {
    private StackSort(){}

    /** Computes the identity's sortable fields once for a settlement group. */
    public static SortKey key(ItemStack stack) {
        DataComponentPatch patch = stack.getComponentsPatch();
        return new SortKey(BuiltInRegistries.ITEM.getKey(stack.getItem()), stack.getDamageValue(),
                patch);
    }

    public static final class SortKey implements Comparable<SortKey> {
        private final Identifier itemId;
        private final int damage;
        private final DataComponentPatch patch;
        private String componentText;

        private SortKey(Identifier itemId, int damage, DataComponentPatch patch) {
            this.itemId = itemId;
            this.damage = damage;
            this.patch = patch;
        }

        private String componentText() {
            if (componentText == null) componentText = patch.toString();
            return componentText;
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
    }
}
