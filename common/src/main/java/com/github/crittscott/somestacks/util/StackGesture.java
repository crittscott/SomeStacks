package com.github.crittscott.somestacks.util;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import javax.annotation.Nullable;

/** Pure gesture intent; target resolution, authority, and loader packet behavior stay with their callers. */
public enum StackGesture {
    NONE, CYCLE_MODE, TOGGLE_PERMANENT, ROTATE_BLOCK, ROTATE_ITEM, DEPOSIT, EXTRACT;

    public static StackGesture classify(@Nullable BlockType clicked, StackMode mode,
                                        boolean modifier, boolean shift, ItemStack held,
                                        boolean hittingBlock, InteractionHand hand) {
        if (hand != InteractionHand.MAIN_HAND) return NONE;
        if (!hittingBlock) return modifier ? CYCLE_MODE : NONE;
        if (modifier && held.isEmpty() && mode == StackMode.TOGGLE_PERMANENT
                && clicked == BlockType.STORAGE_STACK) return TOGGLE_PERMANENT;
        if (shift && held.is(Items.REDSTONE_TORCH)
                && (clicked == BlockType.STORAGE_STACK || clicked == BlockType.SINGLES_STACK)) return ROTATE_BLOCK;
        if (shift && held.is(Items.SOUL_TORCH) && clicked == BlockType.SINGLES_STACK) return ROTATE_ITEM;
        if (modifier && !shift && !held.isEmpty() && mode != StackMode.TOGGLE_PERMANENT) return DEPOSIT;
        return !modifier && !shift && clicked != null ? EXTRACT : NONE;
    }

    /** Empty-hand air cycling has no local item use to suppress. */
    public boolean consumesLocalUse(ItemStack held) {
        return this != NONE && (this != CYCLE_MODE || !held.isEmpty());
    }
}
