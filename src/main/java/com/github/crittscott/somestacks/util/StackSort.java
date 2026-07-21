package com.github.crittscott.somestacks.util;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.Comparator;

public final class StackSort {
    private StackSort(){}

    public static final Comparator<ItemStack> COMPARATOR = (a, b) -> {
        if (a.isEmpty() && b.isEmpty()) return 0;
        if (a.isEmpty()) return 1;
        if (b.isEmpty()) return -1;

        ResourceLocation aKey = ForgeRegistries.ITEMS.getKey(a.getItem());
        ResourceLocation bKey = ForgeRegistries.ITEMS.getKey(b.getItem());
        if (aKey == null || bKey == null) return 0;
        int c = aKey.compareTo(bKey);
        if (c != 0) return c;

        c = Integer.compare(a.getDamageValue(), b.getDamageValue());
        if (c != 0) return c;

        // Untagged variants of an item come first; tagged ones follow in a stable order, so a
        // repacked pile lays its stacks out the same way every time.
        CompoundTag aTag = a.getTag();
        CompoundTag bTag = b.getTag();
        if (aTag == null || bTag == null) {
            if (aTag != bTag) return aTag != null ? 1 : -1;
        } else {
            c = aTag.toString().compareTo(bTag.toString());
            if (c != 0) return c;
        }

        // Fullest first, so a run of one item ends on its single partial stack.
        return Integer.compare(b.getCount(), a.getCount());
    };
}
