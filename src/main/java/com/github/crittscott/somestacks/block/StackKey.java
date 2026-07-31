package com.github.crittscott.somestacks.block;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import javax.annotation.Nullable;

/**
 * Exact stack identity: two stacks merge if and only if their keys are equal.
 *
 * <p>The tag is copied rather than referenced, so a key stays valid once taken. A caller may key a
 * stack it is about to remove from the world, and the tag it holds belongs to that stack until it
 * does.
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
