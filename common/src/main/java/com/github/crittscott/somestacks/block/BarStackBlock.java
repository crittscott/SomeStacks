package com.github.crittscott.somestacks.block;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import javax.annotation.Nullable;

/**
 * The Bar Stack block: the world-facing half of {@link BarStackBE}, holding the block state, the
 * shapes, and the breaking behavior.
 *
 * <p>Its outline and collision follow the occupied bars, so an empty block shows no highlight box
 * while remaining clickable through a full-block interaction shape. Pathfinding always treats the
 * block as obstructed, while its empty internal space causes no suffocation or camera fog.
 *
 * <p>Removing one of these takes the support out from under the bars above, so breaking collapses
 * the column onto the gap. It also carries the deferred publication tick that content edits
 * schedule on the bottom block of the column.
 */
public class BarStackBlock extends ShapedStackBlock {
    public BarStackBlock(Properties props) {
        super(props);
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new BarStackBE(pos, state);
    }

    @Override
    protected VoxelShape occupiedShape(BlockGetter level, BlockPos pos) {
        BlockEntity be = level.getBlockEntity(pos);
        if (be instanceof BarStackBE barBe) {
            return barBe.getCachedShape();
        }
        return Shapes.empty();
    }

    /**
     * Publishes deferred content, lighting, and comparator changes. Edits schedule this tick on the
     * column's bottom block, coalescing a burst of changes anywhere in the run into one pass.
     */
    @Override
    public void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        BarColumn column = BarColumn.at(level, pos);
        if (column != null) {
            column.publishPending();
        }
    }

    /**
     * Invalidates cached columns and publishes the comparator output of the runs this block joins
     * or touches.
     */
    @Override
    public void onPlace(
            BlockState state, Level level, BlockPos pos, BlockState oldState, boolean isMoving) {
        super.onPlace(state, level, pos, oldState, isMoving);
        if (!oldState.is(this)) {
            BarColumn.invalidateAround(level, pos);
            BarColumn.publishAround(level, pos);
        }
    }

    @Override
    public void onRemove(
            BlockState state, Level level, BlockPos pos, BlockState newState, boolean isMoving) {
        if (!state.is(newState.getBlock())) {
            boolean cascading = false;
            ServerPlayer breaker = null;
            BarStackBE.DropBatch drops = new BarStackBE.DropBatch();

            if (level.getBlockEntity(pos) instanceof BarStackBE barBe) {
                cascading = barBe.wasRemovedByCascade();
                breaker = barBe.takeBreakingPlayer();
                drops.addAll(pos, barBe.getItems());
            }
            super.onRemove(state, level, pos, newState, isMoving);
            BarColumn.invalidateAround(level, pos);

            // Removing a block removes the supporting seam beneath the column above. A cascade
            // already walks upward itself, so only an external removal starts another collapse,
            // whose cleanup is the breaking player's doing, or automation's when none broke it.
            if (level instanceof ServerLevel serverLevel) {
                if (!isMoving && !cascading) {
                    BarStackBE.collapseAbove(serverLevel, pos, drops, breaker);
                }
                drops.spawn(serverLevel);
            }

            // Publish after collapse so surviving runs report their final contents.
            BarColumn.publishAround(level, pos);

            // Losing a block from the middle leaves two runs where there was one, and a publication
            // an edit deferred is scheduled on the bottom of the run as it stood. The upper run has
            // its own bottom now, which that tick will never reach, so both sides are scheduled
            // again.
            BarColumn.markDirtyAt(level, pos.below());
            BarColumn.markDirtyAt(level, pos.above());
        }
    }
}
