package com.github.crittscott.somestacks.client.interaction;

import com.github.crittscott.somestacks.util.BlockType;
import com.github.crittscott.somestacks.util.StackMode;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;

/**
 * One right-click, in the terms the rules ask about: who clicked, what they hit, which modifiers
 * were held, and the placement mode in force. A context built from an empty-hand or held-item click
 * has no clicked block, so block-specific queries return false.
 *
 * <p>The context also carries the rules' one output, {@link #cancelEvent()}, which the event
 * handler reads back after the walk to decide whether the click is taken from vanilla.
 */
public final class InteractionContext {
    private final Player player;
    private final InteractionHand hand;
    private final Block clickedBlock;
    private final StackMode currentMode;
    private final boolean modeKeyDown;
    private final boolean hittingBlock;

    private boolean shouldCancel = false;

    private InteractionContext(Player player, InteractionHand hand, Block clickedBlock,
                               StackMode currentMode, boolean modeKeyDown, boolean hittingBlock) {
        this.player = player;
        this.hand = hand;
        this.clickedBlock = clickedBlock;
        this.currentMode = currentMode;
        this.modeKeyDown = modeKeyDown;
        this.hittingBlock = hittingBlock;
    }

    /** Builds an air/item event context with the loader's explicit crosshair classification. */
    public static InteractionContext forAirClick(
            Player player, InteractionHand hand,
            StackMode currentMode, boolean modeKeyDown, boolean hittingBlock) {
        return new InteractionContext(
                player,
                hand,
                null,
                currentMode,
                modeKeyDown,
                hittingBlock
        );
    }

    /** Builds a block context from the clicked position; its hit classification is always block. */
    public static InteractionContext forBlockClick(
            Player player, Level level, InteractionHand hand, BlockPos pos,
            StackMode currentMode, boolean modeKeyDown) {
        Block block = level.getBlockState(pos).getBlock();

        return new InteractionContext(
                player,
                hand,
                block,
                currentMode,
                modeKeyDown,
                true
        );
    }

    public boolean isShift() {
        return player.isShiftKeyDown();
    }

    public boolean isVDown() {
        return modeKeyDown;
    }

    public boolean hasItemInHand() {
        return !player.getMainHandItem().isEmpty();
    }

    public boolean isMainHand() {
        return hand == InteractionHand.MAIN_HAND;
    }

    /**
     * Whether the player's crosshair is on a block. Distinguishes a click at open air, which cycles
     * the placement mode, from one that merely missed the block-click event.
     */
    public boolean isHittingBlock() {
        return hittingBlock;
    }

    public boolean isAnyStackBlock() {
        return clickedBlock != null && BlockType.of(clickedBlock) != null;
    }

    public boolean isStorageStack() {
        return clickedBlock != null && BlockType.of(clickedBlock) == BlockType.STORAGE_STACK;
    }

    /** The stacks that rotate as a whole block; a Bar Stack's bars are fixed to their layer. */
    public boolean isRotatableStack() {
        if (clickedBlock == null) {
            return false;
        }
        BlockType type = BlockType.of(clickedBlock);
        return type == BlockType.STORAGE_STACK || type == BlockType.SINGLES_STACK;
    }

    public boolean isSinglesStack() {
        return clickedBlock != null && BlockType.of(clickedBlock) == BlockType.SINGLES_STACK;
    }

    /**
     * Whether the placement and deposit rules may claim this click. Holding the modifier in Toggle
     * Permanent mode is reserved for the toggle gesture, which would otherwise be shadowed by them.
     */
    public boolean canPlaceOrDeposit() {
        return currentMode != StackMode.TOGGLE_PERMANENT || !isVDown();
    }

    public StackMode getCurrentMode() {
        return currentMode;
    }

    public Player getPlayer() {
        return player;
    }

    public void cancelEvent() {
        shouldCancel = true;
    }

    public boolean shouldCancel() {
        return shouldCancel;
    }
}
