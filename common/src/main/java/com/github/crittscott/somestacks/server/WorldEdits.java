package com.github.crittscott.somestacks.server;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Vanilla world-edit mechanics for automated growth and removal: world border and spawn
 * protection, entity obstruction, and the actual block placement/removal. The one loader-specific
 * decision each carries — who the automation actor is, and whether a claim/logging mod vetoes the
 * edit — is delegated to {@link EditAuthority}.
 */
public final class WorldEdits {
    private WorldEdits() {
    }

    private static EditAuthority authority;

    /** Installed once by the loader's entry point before any world logic can run. */
    public static void setAuthority(EditAuthority impl) {
        authority = impl;
    }

    private static EditAuthority authority() {
        return authority;
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
        return authority().automationActor(level);
    }

    /** Vanilla's placement obstruction test for a block-local collision shape. */
    public static boolean isUnobstructed(ServerLevel level, BlockPos pos, VoxelShape localShape) {
        if (localShape.isEmpty()) {
            return true;
        }
        VoxelShape worldShape = localShape.move(pos.getX(), pos.getY(), pos.getZ());
        return level.getEntities(
                (Entity) null,
                worldShape.bounds(),
                entity -> !entity.isRemoved()
                        && entity.blocksBuilding
                        && Shapes.joinIsNotEmpty(
                                worldShape,
                                Shapes.create(entity.getBoundingBox()),
                                BooleanOp.AND))
                .isEmpty();
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
     * Places {@code state} at {@code pos}, restoring the previous state if a claim/logging mod
     * vetoes it. {@code finalCollision} is the completed placement's collision shape, since a
     * block-entity stack can start empty and acquire its real shape only with its first deposit.
     */
    public static boolean placeChecked(Player placer, ServerLevel level, BlockPos pos,
                                       BlockState state, Direction placedAgainst,
                                       VoxelShape finalCollision) {
        if (level.isOutsideBuildHeight(pos) || !isUnobstructed(level, pos, finalCollision)) {
            return false;
        }
        EditAuthority.PlacementVeto placementVeto = authority().preparePlacement(level, pos);
        BlockState previous = level.getBlockState(pos);
        if (!level.setBlock(pos, state, Block.UPDATE_ALL)) {
            return false;
        }
        if (placementVeto.isVetoed(placer, placedAgainst)) {
            level.setBlock(pos, previous, Block.UPDATE_ALL);
            return false;
        }
        return true;
    }

    /**
     * Removes a stack block the mod itself decided to take down, answering to the same protection
     * growth does and reporting the removal to claim/logging mods.
     *
     * @return whether the block was removed
     */
    public static boolean removeChecked(ServerLevel level, BlockPos pos) {
        if (isProtected(level, pos)) {
            return false;
        }
        BlockState state = level.getBlockState(pos);
        if (authority().vetoesRemoval(level, pos, state)) {
            return false;
        }
        return level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
    }
}
