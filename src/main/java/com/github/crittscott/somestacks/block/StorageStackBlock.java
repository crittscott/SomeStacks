package com.github.crittscott.somestacks.block;

import com.github.crittscott.somestacks.SomeStacks;
import com.github.crittscott.somestacks.util.ItemOps;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;

import javax.annotation.Nullable;

public class StorageStackBlock extends Block implements EntityBlock {
    public static final IntegerProperty LIGHT_LEVEL = IntegerProperty.create("light", 0, 15);

    public StorageStackBlock(Properties props) {
        super(props);
        registerDefaultState(defaultBlockState().setValue(LIGHT_LEVEL, 0));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(LIGHT_LEVEL);
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.ENTITYBLOCK_ANIMATED;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new StorageStackBE(pos, state);
    }

    @Override
    public boolean propagatesSkylightDown(BlockState state, BlockGetter level, BlockPos pos) {
        return true;
    }

    @Override
    public float getShadeBrightness(BlockState state, BlockGetter level, BlockPos pos) {
        return 1.0F;
    }

    @Override
    public int getLightBlock(BlockState state, BlockGetter level, BlockPos pos) {
        return 0;
    }

    @Override
    public boolean hasAnalogOutputSignal(BlockState state) {
        return true;
    }

    /** Reports the whole pile's fill, so a comparator reads the same value anywhere along it. */
    @Override
    public int getAnalogOutputSignal(BlockState state, Level level, BlockPos pos) {
        StoragePile pile = StoragePile.at(level, pos);
        if (pile == null) {
            return 0;
        }
        return (int) Math.round(pile.fillLevel() * 15.0);
    }

    @Override
    public int getLightEmission(BlockState state, BlockGetter level, BlockPos pos) {
        return state.getValue(LIGHT_LEVEL);
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        SomeStacks.LOGGER.debug("StorageStackBlock.use() called.");
        return level.isClientSide ? InteractionResult.SUCCESS : InteractionResult.CONSUME;
    }

    /**
     * Runs the settle a content edit scheduled. Edits mark the pile's base, so a burst of them
     * anywhere in the pile collapses into this one pass.
     */
    @Override
    public void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        StoragePile pile = StoragePile.at(level, pos);
        if (pile != null) {
            pile.settle();
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

            // Losing a block splits one pile into two, or shortens one. Settle both sides rather
            // than leaving the remains unpacked until something else touches them.
            StoragePile.markDirtyAt(level, pos.below());
            StoragePile.markDirtyAt(level, pos.above());
        }
    }
}
