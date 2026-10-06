package com.github.crittscott.somestacks.block;

import com.github.crittscott.somestacks.util.ItemOps;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;

import javax.annotation.Nullable;

/**
 * The Storage Stack block: the world-facing half of {@link StorageStackBE}, holding the block
 * state, shapes, and breaking behavior. Unlike Singles and Bar, Storage keeps a full-block shape
 * regardless of which cells are occupied.
 *
 * <p>Breaking drops only this block's own contents. What that does to the rest of the pile, which
 * may need to settle or shrink around the gap, belongs to {@link StoragePile}.
 */
public class StorageStackBlock extends StackBlock {
    public StorageStackBlock(Properties props) {
        super(props);
        registerDefaultState(defaultBlockState().setValue(HORIZONTAL_FACING, Direction.NORTH));
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
        return new StorageStackBE(pos, state);
    }

    @Override
    public boolean propagatesSkylightDown(BlockState state) {
        return true;
    }

    @Override
    public float getShadeBrightness(BlockState state, BlockGetter level, BlockPos pos) {
        return 1.0F;
    }

    @Override
    public int getLightBlock(BlockState state) {
        return 0;
    }

    /** Reports the whole pile's fill, so a comparator reads the same value anywhere along it. */
    @Override
    public int getAnalogOutputSignal(BlockState state, Level level, BlockPos pos) {
        StoragePile pile = StoragePile.at(level, pos);
        if (pile == null) {
            return 0;
        }
        return pile.comparatorSignal();
    }

    /**
     * Runs a pile pass scheduled at its base. Structural changes can leave an older tick at a
     * former base, so only the current base performs the settle.
     */
    @Override
    public void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        StoragePile pile = StoragePile.at(level, pos);
        if (pile != null && pile.basePos().equals(pos)) {
            pile.settle();
        }
    }

    /** Repairs the run-wide state and derived values affected when this block joins a pile. */
    @Override
    public void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean isMoving) {
        super.onPlace(state, level, pos, oldState, isMoving);
        if (!oldState.is(this)) {
            StoragePile.invalidateAround(level, pos);
            if (!level.isClientSide
                    && level.getBlockEntity(pos) instanceof StorageStackBE placed) {
                StoragePile.adoptNeighbourState(level, pos, placed);
                StoragePile.markDirtyAt(level, pos);
            }
        }
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean isMoving) {
        if (!state.is(newState.getBlock())) {
            BlockEntity be = level.getBlockEntity(pos);
            if (be instanceof StorageStackBE sbe) {
                ItemOps.dropAllItems(sbe.getItems(), level, pos);
            }
            super.onRemove(state, level, pos, newState, isMoving);

            // Before anything resolves a pile again: what the neighbors hold describes a run this
            // block was part of.
            StoragePile.invalidateAround(level, pos);

            // Losing a block splits one pile into two, or shortens one. Settle both sides rather
            // than leaving the remains unpacked until something else touches them.
            StoragePile.markDirtyAt(level, pos.below());
            StoragePile.markDirtyAt(level, pos.above());
        }
    }
}
