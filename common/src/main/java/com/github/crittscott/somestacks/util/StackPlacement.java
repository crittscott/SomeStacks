package com.github.crittscott.somestacks.util;

import com.github.crittscott.somestacks.block.StackBlock;
import com.github.crittscott.somestacks.block.StorageStackBE;
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
     * A stack block's initial state, with waterlogging and neighboring Storage facing resolved.
     *
     * <p>Vanilla reads the fluid at the target through {@code getStateForPlacement}, which runs off
     * a {@code BlockPlaceContext} the mod never builds: every stack block reaches the world through
     * a vanilla interaction or a automation-driven growth instead. This is where those routes ask
     * the same question, so that a stack placed in water displaces it no more than a vanilla slab
     * does.
     */
    public static BlockState stateFor(Block block, LevelReader level, BlockPos pos) {
        boolean inWater = level.getFluidState(pos).getType() == Fluids.WATER;
        BlockState state = block.defaultBlockState().setValue(BlockStateProperties.WATERLOGGED, inWater);
        if (block instanceof StackBlock stack && stack.type() == BlockType.STORAGE_STACK) {
            StorageStackBE neighbour = level.getBlockEntity(pos.below()) instanceof StorageStackBE below
                    ? below : level.getBlockEntity(pos.above()) instanceof StorageStackBE above ? above : null;
            if (neighbour != null) {
                state = state.setValue(StackBlock.HORIZONTAL_FACING,
                        neighbour.getBlockState().getValue(StackBlock.HORIZONTAL_FACING));
            }
        }
        return state;
    }
}
