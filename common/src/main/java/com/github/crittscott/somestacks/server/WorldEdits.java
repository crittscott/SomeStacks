package com.github.crittscott.somestacks.server;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Vanilla world-edit mechanics for stack growth and removal: world border and spawn protection,
 * entity obstruction, actor propagation, and the actual block edit. Automation identity and
 * loader-native claim/logging hooks are delegated to {@link EditAuthority}.
 */
public final class WorldEdits {
    private WorldEdits() {
    }

    private static EditAuthority authority;

    /** Installed once by the loader's entry point before any world logic can run. */
    public static void setAuthority(EditAuthority impl) {
        authority = impl;
    }

    /** Vanilla's own gate on a block interaction: world border and spawn protection. */
    public static boolean isProtected(ServerPlayer sp, BlockPos pos) {
        return !sp.serverLevel().mayInteract(sp, pos);
    }

    /** {@link #isProtected(ServerPlayer, BlockPos)} for automation, using the loader's actor. */
    public static boolean isProtected(ServerLevel level, BlockPos pos) {
        return isProtected(automationActor(level), pos);
    }

    /** The actor automation-driven growth and removal are attributed to. */
    public static ServerPlayer automationActor(ServerLevel level) {
        return authority.automationActor(level);
    }

    /** Vanilla's placement obstruction test for a block-local collision shape. */
    public static boolean isUnobstructed(ServerLevel level, BlockPos pos, VoxelShape localShape) {
        return localShape.isEmpty()
                || level.isUnobstructed(
                        null, localShape.move(pos.getX(), pos.getY(), pos.getZ()));
    }

    /**
     * {@link #placeChecked(Player, ServerLevel, BlockPos, BlockState, Direction, VoxelShape)} using
     * {@code state}'s own collision shape. Block-entity stacks can start empty and acquire their
     * real shape only with their first deposit, so callers that place one use the full overload
     * with the completed shape instead.
     */
    public static boolean placeChecked(Player placer, ServerLevel level, BlockPos pos,
                                       BlockState state, Direction placedAgainst) {
        VoxelShape collision = state.getCollisionShape(level, pos, CollisionContext.empty());
        return placeChecked(placer, level, pos, state, placedAgainst, collision);
    }

    /**
     * Places {@code state} at {@code pos}. Loader checks that can run as queries happen before the
     * world changes; loaders whose native placement event requires the placed state may still veto
     * afterward, in which case the previous state is restored. {@code finalCollision} is the
     * completed placement's collision shape, since a block-entity stack can start empty and acquire
     * its real shape only with its first deposit.
     */
    public static boolean placeChecked(Player placer, ServerLevel level, BlockPos pos,
                                       BlockState state, Direction placedAgainst,
                                       VoxelShape finalCollision) {
        if (level.isOutsideBuildHeight(pos) || !isUnobstructed(level, pos, finalCollision)) {
            return false;
        }
        EditAuthority.PlacementVeto placementVeto = authority.preparePlacement(level, pos);
        if (placementVeto.isVetoedBefore(placer, placedAgainst)) {
            return false;
        }
        BlockState previous = level.getBlockState(pos);
        if (!level.setBlock(pos, state, Block.UPDATE_ALL)) {
            return false;
        }
        if (placementVeto.isVetoedAfter(placer, placedAgainst)) {
            level.setBlock(pos, previous, Block.UPDATE_ALL);
            return false;
        }
        BlockState placedState = level.getBlockState(pos);
        placedState.getBlock().setPlacedBy(
                level, pos, placedState, placer, placer.getMainHandItem());
        level.gameEvent(
                GameEvent.BLOCK_PLACE, pos, GameEvent.Context.of(placer, placedState));
        return true;
    }

    /**
     * Removes a stack block the mod itself decided to take down, answering to the same protection
     * growth does and reporting the removal to claim/logging mods.
     *
     * @return whether the block was removed
     */
    public static boolean removeChecked(ServerLevel level, BlockPos pos) {
        return removeChecked(automationActor(level), level, pos);
    }

    /**
     * Removes a stack block on behalf of {@code actor}, preserving that identity through vanilla
     * protection, loader-native removal events, and the emitted game event.
     */
    public static boolean removeChecked(ServerPlayer actor, ServerLevel level, BlockPos pos) {
        if (isProtected(actor, pos)) {
            return false;
        }
        BlockState state = level.getBlockState(pos);
        BlockEntity blockEntity = level.getBlockEntity(pos);
        if (authority.vetoesRemoval(actor, level, pos, state, blockEntity)) {
            return false;
        }
        if (!level.removeBlock(pos, false)) {
            return false;
        }
        authority.afterRemoval(actor, level, pos, state, blockEntity);
        level.gameEvent(GameEvent.BLOCK_DESTROY, pos, GameEvent.Context.of(actor, state));
        return true;
    }
}
