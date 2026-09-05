package com.github.crittscott.somestacks.block;

import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * Exact stack identity: two stacks merge if and only if their keys are equal.
 *
 * <p>The component patch is immutable, so later mutation or removal of the source stack cannot
 * change the key's equality or hash code.
 */
record StackKey(Item item, int damage, DataComponentPatch components) {
    static StackKey of(ItemStack stack) {
        return new StackKey(
                stack.getItem(),
                stack.getDamageValue(),
                stack.getComponentsPatch());
    }
}
