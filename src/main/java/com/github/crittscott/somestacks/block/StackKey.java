package com.github.crittscott.somestacks.block;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import javax.annotation.Nullable;

/**
 * Exact stack identity: two stacks merge if and only if their keys are equal.
 *
 * <p>The tag is copied so later mutation or removal of the source stack cannot change the key's
 * equality or hash code.
 */
record StackKey(Item item, int damage, @Nullable CompoundTag tag) {
    static StackKey of(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        return new StackKey(
                stack.getItem(),
                stack.getDamageValue(),
                tag == null ? null : tag.copy());
    }
}
