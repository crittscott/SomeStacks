package com.github.crittscott.somestacks.util;

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

        boolean anbt = a.hasTag();
        boolean bnbt = b.hasTag();
        if (anbt != bnbt) return anbt ? 1 : -1;

        // Prefer partials first so they get filled before full stacks
        return Integer.compare(a.getCount(), b.getCount());
    };
}
