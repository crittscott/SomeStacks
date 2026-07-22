package com.github.crittscott.somestacks.client.interaction;

import com.github.crittscott.somestacks.ModRegistry;
import com.github.crittscott.somestacks.client.KeyMappings;
import com.github.crittscott.somestacks.util.StackMode;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.HitResult;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;

public final class InteractionContext {
    private final Player player;
    private final Level level;
    private final InteractionHand hand;
    private final BlockPos clickedPos;
    private final Block clickedBlock;
    private final Direction face;
    private final StackMode currentMode;

    private boolean shouldCancel = false;

    private InteractionContext(Player player, Level level, InteractionHand hand,
                               BlockPos clickedPos, Block clickedBlock, Direction face, StackMode currentMode) {
        this.player = player;
        this.level = level;
        this.hand = hand;
        this.clickedPos = clickedPos;
        this.clickedBlock = clickedBlock;
        this.face = face;
        this.currentMode = currentMode;
    }

    public static InteractionContext forEmptyHand(PlayerInteractEvent.RightClickEmpty evt, StackMode currentMode) {
        return new InteractionContext(
                evt.getEntity(),
                evt.getLevel(),
                evt.getHand(),
                null,
                null,
                null,
                currentMode
        );
    }

    public static InteractionContext forItemInHand(PlayerInteractEvent.RightClickItem evt, StackMode currentMode) {
        return new InteractionContext(
                evt.getEntity(),
                evt.getLevel(),
                evt.getHand(),
                null,
                null,
                null,
                currentMode
        );
    }

    public static InteractionContext forBlockClick(PlayerInteractEvent.RightClickBlock evt, StackMode currentMode) {
        BlockPos pos = evt.getPos();
        Level level = evt.getLevel();
        Block block = level.getBlockState(pos).getBlock();

        return new InteractionContext(
                evt.getEntity(),
                level,
                evt.getHand(),
                pos,
                block,
                evt.getFace(),
                currentMode
        );
    }

    public boolean isShift() {
        return player.isShiftKeyDown();
    }

    public boolean isVDown() {
        return KeyMappings.STACK_MODE_KEY.isDown();
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
        return clickedBlock == ModRegistry.STORAGE_STACK_BLOCK.get()
                || clickedBlock == ModRegistry.SINGLES_STACK_BLOCK.get()
                || clickedBlock == ModRegistry.BAR_STACK_BLOCK.get();
    }

    public boolean isStorageStack() {
        if (clickedBlock == null) {
            return false;
        }
        return clickedBlock == ModRegistry.STORAGE_STACK_BLOCK.get();
    }

    /** The stacks that rotate as a whole block; a Bar Stack's bars are fixed to their layer. */
    public boolean isRotatableStack() {
        if (clickedBlock == null) {
            return false;
        }
        return clickedBlock == ModRegistry.STORAGE_STACK_BLOCK.get()
                || clickedBlock == ModRegistry.SINGLES_STACK_BLOCK.get();
    }

    public boolean isSinglesStack() {
        return clickedBlock != null && clickedBlock == ModRegistry.SINGLES_STACK_BLOCK.get();
    }

    public boolean isSinglesOrBarStack(Block block) {
        return block == ModRegistry.SINGLES_STACK_BLOCK.get()
                || block == ModRegistry.BAR_STACK_BLOCK.get();
    }

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
