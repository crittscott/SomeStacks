package com.github.crittscott.somestacks.util;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.material.Fluids;

/** The state a stack block takes when it is put into the world. */
public final class StackPlacement {
    private StackPlacement() {}

    /**
     * A stack block's default state, waterlogged when it is going into water.
     *
     * <p>Vanilla reads the fluid at the target through {@code getStateForPlacement}, which runs off
     * a {@code BlockPlaceContext} the mod never builds: every stack block reaches the world through
     * a gesture packet or a capability-driven growth instead. This is where those four routes ask
     * the same question, so that a stack placed in water displaces it no more than a vanilla slab
     * does.
     */
    public static BlockState stateFor(Block block, LevelReader level, BlockPos pos) {
        boolean inWater = level.getFluidState(pos).getType() == Fluids.WATER;
        return block.defaultBlockState().setValue(BlockStateProperties.WATERLOGGED, inWater);
    }
}
