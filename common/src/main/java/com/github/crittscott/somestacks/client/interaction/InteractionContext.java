package com.github.crittscott.somestacks.client.interaction;

import com.github.crittscott.somestacks.CommonRegistry;
import com.github.crittscott.somestacks.util.StackMode;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.HitResult;

/**
 * One right-click, in the terms the rules ask about: who clicked, what they hit, which modifiers
 * were held, and the placement mode in force. A context built from an empty-hand or held-item click
 * has no clicked position, block, or face, so block-specific queries return false.
 *
 * <p>The context also carries the rules' one output, {@link #cancelEvent()}, which the event
 * handler reads back after the walk to decide whether the click is taken from vanilla.
 */
public final class InteractionContext {
    private final Player player;
    private final Level level;
    private final InteractionHand hand;
    private final BlockPos clickedPos;
    private final Block clickedBlock;
    private final Direction face;
    private final StackMode currentMode;
    private final boolean modeKeyDown;

    private boolean shouldCancel = false;

    private InteractionContext(Player player, Level level, InteractionHand hand,
                               BlockPos clickedPos, Block clickedBlock, Direction face,
                               StackMode currentMode, boolean modeKeyDown) {
        this.player = player;
        this.level = level;
        this.hand = hand;
        this.clickedPos = clickedPos;
        this.clickedBlock = clickedBlock;
        this.face = face;
        this.currentMode = currentMode;
        this.modeKeyDown = modeKeyDown;
    }

    public static InteractionContext forAirClick(
            Player player, Level level, InteractionHand hand,
            StackMode currentMode, boolean modeKeyDown) {
        return new InteractionContext(
                player,
                level,
                hand,
                null,
                null,
                null,
                currentMode,
                modeKeyDown
        );
    }

    public static InteractionContext forBlockClick(
            Player player, Level level, InteractionHand hand, BlockPos pos, Direction face,
            StackMode currentMode, boolean modeKeyDown) {
        Block block = level.getBlockState(pos).getBlock();

        return new InteractionContext(
                player,
                level,
                hand,
                pos,
                block,
                face,
                currentMode,
                modeKeyDown
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

    public boolean isClientSide() {
        return level.isClientSide;
    }

    /**
     * Whether the player's crosshair is on a block. Distinguishes a click at open air, which cycles
     * the placement mode, from one that merely missed the block-click event.
     */
    public boolean isHittingBlock() {
        if (!level.isClientSide) {
            return false;
        }
        HitResult hit = Minecraft.getInstance().hitResult;
        return hit != null && hit.getType() == HitResult.Type.BLOCK;
    }

    public boolean isAnyStackBlock() {
        if (clickedBlock == null) {
            return false;
        }
        return clickedBlock == CommonRegistry.STORAGE_STACK_BLOCK.get()
                || clickedBlock == CommonRegistry.SINGLES_STACK_BLOCK.get()
                || clickedBlock == CommonRegistry.BAR_STACK_BLOCK.get();
    }

    public boolean isStorageStack() {
        if (clickedBlock == null) {
            return false;
        }
        return clickedBlock == CommonRegistry.STORAGE_STACK_BLOCK.get();
    }

    /** The stacks that rotate as a whole block; a Bar Stack's bars are fixed to their layer. */
    public boolean isRotatableStack() {
        if (clickedBlock == null) {
            return false;
        }
        return clickedBlock == CommonRegistry.STORAGE_STACK_BLOCK.get()
                || clickedBlock == CommonRegistry.SINGLES_STACK_BLOCK.get();
    }

    public boolean isSinglesStack() {
        return clickedBlock != null && clickedBlock == CommonRegistry.SINGLES_STACK_BLOCK.get();
    }

    public boolean isSinglesOrBarStack(Block block) {
        return block == CommonRegistry.SINGLES_STACK_BLOCK.get()
                || block == CommonRegistry.BAR_STACK_BLOCK.get();
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

    public BlockPos getClickedPos() {
        return clickedPos;
    }

    public Direction getFace() {
        return face;
    }

    public Player getPlayer() {
        return player;
    }

    public Level getLevel() {
        return level;
    }

    public InteractionHand getHand() {
        return hand;
    }

    public void cancelEvent() {
        shouldCancel = true;
    }

    public boolean shouldCancel() {
        return shouldCancel;
    }
}
