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
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import javax.annotation.Nullable;

/**
 * The Bar Stack block: the world-facing half of {@link BarStackBE}, holding the block state, the
 * shapes, and the breaking behaviour.
 *
 * <p>Its outline and collision follow the occupied bars, so an empty block shows no highlight box
 * while still answering clicks through a full-block interaction shape. Neither shape lets mobs path
 * through it, and neither suffocates a player standing in its empty space.
 *
 * <p>Removing one of these takes the support out from under the bars above, so breaking collapses
 * the column onto the gap. It also carries the deferred publication tick that content edits
 * schedule on the bottom block of the column.
 */
public class BarStackBlock extends Block implements EntityBlock, SimpleWaterloggedBlock {
    /**
     * Light this block emits, derived from the {@code BlockItem}s stored in it and kept in the
     * block state so lighting updates travel by the ordinary block-state path.
     */
    public static final IntegerProperty LIGHT_LEVEL = IntegerProperty.create("light", 0, ItemOps.MAX_LIGHT_LEVEL);

    public static final BooleanProperty WATERLOGGED = BlockStateProperties.WATERLOGGED;
    private static final VoxelShape FULL_BLOCK_SHAPE = Shapes.block();

    public BarStackBlock(Properties props) {
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

    /**
     * The whole cube, so the block can be clicked and broken through the gaps between its bars and
     * while it is empty. An empty block therefore draws no highlight box, having no outline shape,
     * while still answering a click; that is the intended trade.
     */
    @Override
    public VoxelShape getInteractionShape(BlockState state, BlockGetter level, BlockPos pos) {
        return FULL_BLOCK_SHAPE;
    }

    /**
     * Nothing, so standing inside a partly filled block neither suffocates nor fogs the camera.
     * Collision already keeps a player out of a bar, which leaves the empty space, and empty space
     * is what a player is entitled to stand in.
     */
    @Override
    public VoxelShape getVisualShape(BlockState state, BlockGetter level, BlockPos pos,
                                     CollisionContext ctx) {
        return Shapes.empty();
    }

    /**
     * Never a way through. The inherited answer is drawn from whether the collision shape fills the
     * block, which no Bar Stack ever does, so mobs would be routed straight into the bars and left
     * grinding against them. Routing around costs a block of clearance and nothing else: the only
     * case this overstates is an empty block, and an empty block rarely stands for long.
     */
    @Override
    public boolean isPathfindable(BlockState state, BlockGetter level, BlockPos pos,
                                  PathComputationType type) {
        return false;
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
