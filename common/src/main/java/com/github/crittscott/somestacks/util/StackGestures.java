package com.github.crittscott.somestacks.util;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import javax.annotation.Nullable;

/**
 * Which stack gesture a right-click makes, from the inputs both sides know: the Stack Modifier,
 * sneaking, the selected mode, the main-hand item, and the clicked block's stack type. The server
 * actions and the client's local claim both ask these, so the client consumes exactly the clicks
 * the server acts on. Targeting inside a stack is the server's alone.
 */
public final class StackGestures {
    private StackGestures() {}

    /** An unmodified, nonsneaking click on a stack extracts the targeted contents. */
    public static boolean extracts(boolean modifierDown, boolean sneaking) {
        return !modifierDown && !sneaking;
    }

    /**
     * A nonsneaking modifier click holding an item, in a placement mode, deposits into the clicked
     * stack or places the selected type against the clicked face.
     */
    public static boolean modifiedItemClick(
            boolean modifierDown, StackMode mode, boolean sneaking, ItemStack held) {
        return modifierDown && mode != StackMode.TOGGLE_PERMANENT && !sneaking && !held.isEmpty();
    }

    /**
     * Whether the Stack Modifier claims a main-hand item use. A modifier right-click at air cycles
     * the placement mode, and one that no block gesture claimed is no item use either; the held
     * item must not then be used (thrown, eaten, drawn).
     */
    public static boolean modifierClaimsItemUse(InteractionHand hand, boolean modifierDown) {
        return hand == InteractionHand.MAIN_HAND && modifierDown;
    }

    /** A sneaking redstone-torch click rotates a Storage or Singles layout; Bar has none. */
    public static boolean rotatesBlock(
            boolean sneaking, ItemStack held, @Nullable BlockType clicked) {
        return sneaking
                && held.is(Items.REDSTONE_TORCH)
                && (clicked == BlockType.STORAGE_STACK || clicked == BlockType.SINGLES_STACK);
    }

    /** A sneaking soul-torch click rotates one Singles item. */
    public static boolean rotatesItem(
            boolean sneaking, ItemStack held, @Nullable BlockType clicked) {
        return sneaking && held.is(Items.SOUL_TORCH) && clicked == BlockType.SINGLES_STACK;
    }

    /**
     * An empty-handed modifier click on Storage in Toggle Permanent mode toggles the pile's
     * permanence, sneaking or not.
     */
    public static boolean togglesPermanence(
            boolean modifierDown, StackMode mode, ItemStack held, @Nullable BlockType clicked) {
        return modifierDown
                && held.isEmpty()
                && mode == StackMode.TOGGLE_PERMANENT
                && clicked == BlockType.STORAGE_STACK;
    }
}
