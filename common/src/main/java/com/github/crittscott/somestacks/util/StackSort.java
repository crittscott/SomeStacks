package com.github.crittscott.somestacks.util;

import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.core.registries.BuiltInRegistries;
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

        ResourceLocation aKey = BuiltInRegistries.ITEM.getKey(a.getItem());
        ResourceLocation bKey = BuiltInRegistries.ITEM.getKey(b.getItem());
        int c = aKey.compareTo(bKey);
        if (c != 0) return c;

        c = Integer.compare(a.getDamageValue(), b.getDamageValue());
        if (c != 0) return c;

        // Component-free variants precede variants carrying components; patch text gives the latter
        // a stable order.
        DataComponentPatch aPatch = a.getComponentsPatch();
        DataComponentPatch bPatch = b.getComponentsPatch();
        boolean aPlain = aPatch.isEmpty();
        boolean bPlain = bPatch.isEmpty();
        if (aPlain || bPlain) {
            if (aPlain != bPlain) return aPlain ? -1 : 1;
        } else {
            c = aPatch.toString().compareTo(bPatch.toString());
            if (c != 0) return c;
        }

        // Fullest first leaves a group's partial stack at its end.
        return Integer.compare(b.getCount(), a.getCount());
    };
}
