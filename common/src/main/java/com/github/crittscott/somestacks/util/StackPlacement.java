package com.github.crittscott.somestacks.util;

import com.github.crittscott.somestacks.block.StorageStackBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.material.Fluids;

/** The state a stack block takes when it is put into the world. */
public final class StackPlacement {
    private StackPlacement() {}

    /**
     * A stack block's placement state, waterlogged when it is going into water. Storage joins
     * inherit the facing below first, then above; other placements use the default facing.
     *
     * <p>Vanilla reads the fluid at the target through {@code getStateForPlacement}, which runs off
     * a {@code BlockPlaceContext} the mod never builds: every stack block reaches the world through
     * a block-use gesture or capability-driven growth instead. This is where those four routes ask
     * the same question, so that a stack placed in water displaces it no more than a vanilla slab
     * does.
     */
    public static BlockState stateFor(Block block, LevelReader level, BlockPos pos) {
        Direction facing = Direction.NORTH;
        if (block instanceof StorageStackBlock) {
            BlockState below = level.getBlockState(pos.below());
            BlockState above = level.getBlockState(pos.above());
            if (below.is(block)) {
                facing = below.getValue(BlockStateProperties.HORIZONTAL_FACING);
            } else if (above.is(block)) {
                facing = above.getValue(BlockStateProperties.HORIZONTAL_FACING);
            }
        }
        return stateFor(block, level, pos, facing);
    }

    /** Builds the final placement state with an explicit layout facing, including Storage growth. */
    public static BlockState stateFor(Block block, LevelReader level, BlockPos pos, Direction facing) {
        boolean inWater = level.getFluidState(pos).getType() == Fluids.WATER;
        BlockState state = block.defaultBlockState().setValue(BlockStateProperties.WATERLOGGED, inWater);
        return state.hasProperty(BlockStateProperties.HORIZONTAL_FACING)
                ? state.setValue(BlockStateProperties.HORIZONTAL_FACING, facing) : state;
    }
}
