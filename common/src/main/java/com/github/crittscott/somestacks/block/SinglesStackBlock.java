package com.github.crittscott.somestacks.block;

import com.github.crittscott.somestacks.CommonRegistry;

import com.github.crittscott.somestacks.util.BlockType;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

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
    @Override
    public BlockType type() {
        return BlockType.SINGLES_STACK;
    }

    public SinglesStackBlock(Properties props) {
        super(props);
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return CommonRegistry.singlesStackBe().create(pos, state);
    }

}
