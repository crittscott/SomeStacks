package com.github.crittscott.somestacks.block;

import com.github.crittscott.somestacks.util.ItemOps;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.SimpleWaterloggedBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.BlockHitResult;

import javax.annotation.Nullable;

/**
 * The Storage Stack block: the world-facing half of {@link StorageStackBE}, holding the block
 * state, shapes, and breaking behavior. Unlike Singles and Bar, Storage keeps a full-block shape
 * regardless of which cells are occupied.
 *
 * <p>Breaking drops only this block's own contents. What that does to the rest of the pile, which
 * may need to settle or shrink around the gap, belongs to {@link StoragePile}.
 */
public class StorageStackBlock extends Block implements EntityBlock, SimpleWaterloggedBlock {
    /**
     * Light this block emits, derived from the {@code BlockItem}s stored in it and kept in the
     * block state so lighting updates travel by the ordinary block-state path.
     */
    public static final IntegerProperty LIGHT_LEVEL = IntegerProperty.create("light", 0, ItemOps.MAX_LIGHT_LEVEL);

    public static final BooleanProperty WATERLOGGED = BlockStateProperties.WATERLOGGED;

    public StorageStackBlock(Properties props) {
        super(props);
        registerDefaultState(defaultBlockState()
                .setValue(LIGHT_LEVEL, 0)
                .setValue(WATERLOGGED, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(LIGHT_LEVEL, WATERLOGGED);
    }

    @Override
    public FluidState getFluidState(BlockState state) {
        return state.getValue(WATERLOGGED)
                ? Fluids.WATER.getSource(false)
                : super.getFluidState(state);
    }

    @Override
    public BlockState updateShape(BlockState state, Direction direction, BlockState neighborState,
                                  LevelAccessor level, BlockPos pos, BlockPos neighborPos) {
        if (state.getValue(WATERLOGGED)) {
            level.scheduleTick(pos, Fluids.WATER, Fluids.WATER.getTickDelay(level));
        }
        return super.updateShape(state, direction, neighborState, level, pos, neighborPos);
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
        return pile.comparatorSignal();
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        return level.isClientSide ? InteractionResult.SUCCESS : InteractionResult.CONSUME;
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
