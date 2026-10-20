package com.github.crittscott.somestacks.block;

import com.github.crittscott.somestacks.util.BlockType;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
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
    @Override
    public BlockType type() {
        return BlockType.BAR_STACK;
    }

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

    /** Invalidates cached columns and schedules publication after this block joins a run. */
    @Override
    public void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean isMoving) {
        super.onPlace(state, level, pos, oldState, isMoving);
        if (!oldState.is(this)) {
            BarColumn.invalidateAround(level, pos);
            BarColumn.publishAround(level, pos);
        }
    }

    /** Publishes surviving runs after the block entity has completed its removal cascade. */
    @Override
    public void affectNeighborsAfterRemoval(BlockState state, ServerLevel level, BlockPos pos, boolean isMoving) {
        if (level.getBlockState(pos).is(this)) return;
        BarColumn.invalidateAround(level, pos);
        super.affectNeighborsAfterRemoval(state, level, pos, isMoving);
        BarColumn.publishAround(level, pos);

        // Both surviving runs need publication; the upper run has a new base for its deferred tick.
        BarColumn.markDirtyAt(level, pos.below());
        BarColumn.markDirtyAt(level, pos.above());
    }

    /** Reports the whole column's fill, so a comparator reads the same value anywhere along it. */
    @Override
    public int getAnalogOutputSignal(BlockState state, Level level, BlockPos pos, Direction direction) {
        BarColumn column = BarColumn.at(level, pos);
        if (column == null) {
            return 0;
        }
        return column.comparatorSignal();
    }
}
