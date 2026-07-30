package com.github.crittscott.somestacks.block;

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
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import javax.annotation.Nullable;

public class BarStackBlock extends Block implements EntityBlock {
    public static final IntegerProperty LIGHT_LEVEL = IntegerProperty.create("light", 0, 15);
    private static final VoxelShape FULL_BLOCK_SHAPE = Shapes.block();

    public BarStackBlock(Properties props) {
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
        return new BarStackBE(pos, state);
    }

    @Override
    public int getLightEmission(BlockState state, BlockGetter level, BlockPos pos) {
        return state.getValue(LIGHT_LEVEL);
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        BlockEntity be = level.getBlockEntity(pos);
        if (be instanceof BarStackBE barBe) {
            return barBe.getCachedShape();
        }
        return Shapes.empty();
    }

    @Override
    public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return getShape(state, level, pos, ctx);
    }

    @Override
    public VoxelShape getInteractionShape(BlockState state, BlockGetter level, BlockPos pos) {
        return FULL_BLOCK_SHAPE;
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        return level.isClientSide ? InteractionResult.SUCCESS : InteractionResult.CONSUME;
    }

    /**
     * Pays the publication a content edit deferred. Edits schedule this on the column's bottom
     * block, so a burst of them anywhere in the run collapses into one pass.
     */
    @Override
    public void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        BarColumn column = BarColumn.at(level, pos);
        if (column != null) {
            column.publishPending();
        }
    }

    /** Joining a column changes what its blocks resolve to, so their held columns are dropped. */
    @Override
    public void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean isMoving) {
        super.onPlace(state, level, pos, oldState, isMoving);
        if (!oldState.is(this)) {
            BarColumn.invalidateAround(level, pos);
            BarColumn.publishAround(level, pos);
        }
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean isMoving) {
        if (!state.is(newState.getBlock())) {
            boolean cascading = false;
            BarDropBatch drops = new BarDropBatch();

            BlockEntity be = level.getBlockEntity(pos);
            if (be instanceof BarStackBE barBe) {
                cascading = barBe.wasRemovedByCascade();
                drops.addAll(pos, barBe.getItems());
            }
            super.onRemove(state, level, pos, newState, isMoving);
            BarColumn.invalidateAround(level, pos);

            // The column above has lost the seam it stood on, so it comes down — the same answer a
            // cascade gives when it empties a block out from under one. A cascade already walks its
            // own way up, so only a removal from outside one starts the collapse.
            if (!isMoving && !cascading) {
                BarStackBE.collapseAbove(level, pos, drops);
            }

            // After the collapse, so the runs left standing publish what they settled on rather
            // than what they held on the way there.
            BarColumn.publishAround(level, pos);

            // Losing a block from the middle leaves two runs where there was one, and a publication
            // an edit deferred is scheduled on the bottom of the run as it stood. The upper run has
            // its own bottom now, which that tick will never reach, so both sides are scheduled
            // again.
            BarColumn.markDirtyAt(level, pos.below());
            BarColumn.markDirtyAt(level, pos.above());
            drops.spawn(level);
        }
    }

    @Override
    public boolean hasAnalogOutputSignal(BlockState state) {
        return true;
    }

    /** Reports the whole column's fill, so a comparator reads the same value anywhere along it. */
    @Override
    public int getAnalogOutputSignal(BlockState state, Level level, BlockPos pos) {
        BarColumn column = BarColumn.at(level, pos);
        if (column == null) {
            return 0;
        }
        return column.comparatorSignal();
    }
}
