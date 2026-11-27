package com.github.crittscott.somestacks.client.interaction;

import com.github.crittscott.somestacks.util.BlockType;
import com.github.crittscott.somestacks.util.StackMode;

import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;

/**
 * One right-click, in the terms the rules ask about: who clicked, with which hand, which stack type
 * they hit, whether the Stack Modifier and sneak were held, and the placement mode in force. A
 * context built from an air or held-item click has no clicked block, so its clicked type is null.
 *
 * <p>The context also carries the rules' one output, {@link #cancelEvent()}, which the event
 * handler reads back after the walk to decide whether the click is taken from vanilla.
 */
public final class InteractionContext {
    @Nullable private final BlockType clickedType;
    private final StackMode currentMode;
    private final InteractionHand hand;
    private final boolean hittingBlock;
    private final boolean modifierDown;
    private final Player player;

    private boolean shouldCancel = false;

    private InteractionContext(
            Player player,
            InteractionHand hand,
            @Nullable BlockType clickedType,
            StackMode currentMode,
            boolean modifierDown,
            boolean hittingBlock) {
        this.player = player;
        this.hand = hand;
        this.clickedType = clickedType;
        this.currentMode = currentMode;
        this.modifierDown = modifierDown;
        this.hittingBlock = hittingBlock;
    }

    public void cancelEvent() {
        shouldCancel = true;
    }

    /** Builds an air/item event context with the loader's explicit crosshair classification. */
    public static InteractionContext forAirClick(
            Player player,
            InteractionHand hand,
            StackMode currentMode,
            boolean modifierDown,
            boolean hittingBlock) {
        return new InteractionContext(
                player, hand, null, currentMode, modifierDown, hittingBlock);
    }

    /** Builds a block context from the clicked position; its hit classification is always block. */
    public static InteractionContext forBlockClick(
            Player player,
            Level level,
            InteractionHand hand,
            BlockPos pos,
            StackMode currentMode,
            boolean modifierDown) {
        return new InteractionContext(
                player,
                hand,
                BlockType.of(level.getBlockState(pos).getBlock()),
                currentMode,
                modifierDown,
                true);
    }

    /** The clicked block's stack type, or null for an ordinary block or an air click. */
    @Nullable
    public BlockType getClickedType() {
        return clickedType;
    }

    public StackMode getCurrentMode() {
        return currentMode;
    }

    /** The main-hand item, which every gesture reads whichever hand the click came from. */
    public ItemStack getHeld() {
        return player.getMainHandItem();
    }

    public Player getPlayer() {
        return player;
    }

    public boolean hasItemInHand() {
        return !player.getMainHandItem().isEmpty();
    }

    /**
     * Whether the player's crosshair is on a block. Distinguishes a click at open air, which cycles
     * the placement mode, from one that merely missed the block-click event.
     */
    public boolean isHittingBlock() {
        return hittingBlock;
    }

    public boolean isMainHand() {
        return hand == InteractionHand.MAIN_HAND;
    }

    public boolean isModifierDown() {
        return modifierDown;
    }

    public boolean isShift() {
        return player.isShiftKeyDown();
    }

    public boolean shouldCancel() {
        return shouldCancel;
    }
}
