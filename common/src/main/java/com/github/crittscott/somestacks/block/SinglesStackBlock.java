package com.github.crittscott.somestacks.block;

import com.github.crittscott.somestacks.util.BlockType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import javax.annotation.Nullable;

/**
 * The Singles Stack block: the world-facing half of {@link SinglesStackBE}, holding the block
 * state, the shapes, and the breaking behavior.
 *
 * <p>Its outline and collision follow the occupied cells, so an empty block shows no highlight box
 * while remaining clickable through a full-block interaction shape. Pathfinding always treats the
 * block as obstructed, while its empty internal space causes no suffocation or camera fog.
 *
 * <p>It also carries the deferred publication tick that content edits schedule on the bottom block
 * of the column.
 */
public class SinglesStackBlock extends ShapedStackBlock {
    public SinglesStackBlock(Properties props) {
        super(props);
        registerDefaultState(defaultBlockState().setValue(HORIZONTAL_FACING, Direction.NORTH));
    }

    @Override
    public BlockType type() {
        return BlockType.SINGLES_STACK;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(HORIZONTAL_FACING);
    }

    @Override
    public BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(
                HORIZONTAL_FACING, rotation.rotate(state.getValue(HORIZONTAL_FACING)));
    }

    @Override
    public BlockState mirror(BlockState state, Mirror mirror) {
        return rotate(state, mirror.getRotation(state.getValue(HORIZONTAL_FACING)));
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new SinglesStackBE(pos, state);
    }

    @Override
    protected VoxelShape occupiedShape(BlockGetter level, BlockPos pos) {
        BlockEntity be = level.getBlockEntity(pos);
        if (be instanceof SinglesStackBE ssbe) {
            return ssbe.getCachedShape();
        }
        return Shapes.empty();
    }

    /**
     * Publishes deferred content, lighting, and comparator changes. Edits schedule this tick on the
     * column's bottom block, coalescing a burst of changes anywhere in the run into one pass.
     */
    @Override
    public void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        SinglesColumn column = SinglesColumn.at(level, pos);
        if (column != null) {
            column.publishPending();
        }
    }

    /** Invalidates cached columns and schedules publication after this block joins a run. */
    @Override
    public void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean isMoving) {
        super.onPlace(state, level, pos, oldState, isMoving);
        if (!oldState.is(this)) {
            SinglesColumn.invalidateAround(level, pos);
            SinglesColumn.publishAround(level, pos);
        }
    }

    /** Publishes both surviving columns after removal, without moving items across the gap. */
    @Override
    public void affectNeighborsAfterRemoval(BlockState state, ServerLevel level, BlockPos pos, boolean isMoving) {
        if (level.getBlockState(pos).is(this)) return;
        SinglesColumn.invalidateAround(level, pos);
        super.affectNeighborsAfterRemoval(state, level, pos, isMoving);
        SinglesColumn.publishAround(level, pos);

        // Both surviving runs need publication; the upper run has a new base for its deferred tick.
        SinglesColumn.markDirtyAt(level, pos.below());
        SinglesColumn.markDirtyAt(level, pos.above());
    }

    /** Reports the whole column's fill, so a comparator reads the same value anywhere along it. */
    @Override
    public int getAnalogOutputSignal(BlockState state, Level level, BlockPos pos, Direction direction) {
        SinglesColumn column = SinglesColumn.at(level, pos);
        if (column == null) {
            return 0;
        }
        return column.comparatorSignal();
    }
}
