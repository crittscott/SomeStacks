package com.github.crittscott.somestacks.block;

import com.github.crittscott.somestacks.util.BlockType;
import com.github.crittscott.somestacks.util.ItemOps;
import com.github.crittscott.somestacks.util.SinglesCubeIdx;
import com.github.crittscott.somestacks.util.ViewRay;

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
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.phys.shapes.CollisionContext;
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
public class SinglesStackBlock extends StackBlock {
    /** The layout rotation, so structure rotation and mirroring turn the layout with the block. */
    public static final DirectionProperty HORIZONTAL_FACING =
            BlockStateProperties.HORIZONTAL_FACING;

    private static final VoxelShape FULL_BLOCK_SHAPE = Shapes.block();

    public SinglesStackBlock(Properties props) {
        super(props, BlockType.SINGLES_STACK);
        registerDefaultState(defaultBlockState().setValue(HORIZONTAL_FACING, Direction.NORTH));
    }

    /** Shared mining, lighting, collision, and piston settings for this stack type. */
    public static Properties properties() {
        return properties(MapColor.WOOD).dynamicShape();
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(HORIZONTAL_FACING);
    }

    /**
     * The one cell the deposit would fill: the target along the ray in a new block, which is
     * unrotated, provided it is supported, which in an empty block means a bottom cell over an
     * occupied seam or the world.
     */
    @Nullable
    @Override
    public VoxelShape firstDepositShape(Level level, BlockPos pos, BlockState state, ViewRay ray) {
        int index =
                ray.depositTarget(
                        pos, SinglesStackBE.SLOTS, i -> SinglesCubeIdx.GRID.localBox(i, 0), null);
        return index >= 0
                        && SinglesCubeIdx.freshBlockSupports(
                                index, SinglesStackBE.seamBeneath(level, pos))
                ? SinglesCubeIdx.GRID.localShape(index, 0)
                : null;
    }

    @Override
    public VoxelShape getCollisionShape(
            BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return getShape(state, level, pos, ctx);
    }

    /**
     * Uses the full cube so gaps and empty blocks remain clickable. The separate outline shape may
     * still be empty, allowing an empty block to omit the highlight box without becoming
     * unselectable.
     */
    @Override
    public VoxelShape getInteractionShape(BlockState state, BlockGetter level, BlockPos pos) {
        return FULL_BLOCK_SHAPE;
    }

    @Override
    public VoxelShape getShape(
            BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        BlockEntity be = level.getBlockEntity(pos);
        if (be instanceof SinglesStackBE ssbe) {
            return ssbe.getCachedShape();
        }
        return Shapes.empty();
    }

    /**
     * Returns no visual occlusion, so empty space inside a partly filled block neither suffocates a
     * player nor fogs the camera. The occupied cells remain solid through the collision shape.
     */
    @Override
    public VoxelShape getVisualShape(
            BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return Shapes.empty();
    }

    /**
     * Prevents pathfinding through the block. The dynamic collision shape never fills the cube, so
     * the inherited result would route mobs through occupied cells. This deliberately treats an
     * empty Singles Stack as blocked as well.
     */
    @Override
    public boolean isPathfindable(
            BlockState state, BlockGetter level, BlockPos pos, PathComputationType type) {
        return false;
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

    /** Invalidates cached columns and schedules publication after this block joins a run. */
    @Override
    public void onPlace(
            BlockState state, Level level, BlockPos pos, BlockState oldState, boolean isMoving) {
        super.onPlace(state, level, pos, oldState, isMoving);
        if (!oldState.is(this)) {
            SinglesColumn.invalidateAround(level, pos);
            SinglesColumn.publishAround(level, pos);
        }
    }

    @Override
    public void onRemove(
            BlockState state, Level level, BlockPos pos, BlockState newState, boolean isMoving) {
        if (!state.is(newState.getBlock())) {
            BlockEntity be = level.getBlockEntity(pos);
            if (be instanceof SinglesStackBE ssbe) {
                ItemOps.dropAllItems(ssbe.getItems(), level, pos);
            }
            super.onRemove(state, level, pos, newState, isMoving);
            SinglesColumn.invalidateAround(level, pos);
            SinglesColumn.publishAround(level, pos);

            // Losing a block from the middle leaves two runs where there was one, and a publication
            // an edit deferred is scheduled on the bottom of the run as it stood. The upper run has
            // its own bottom now, which that tick will never reach, so both sides are scheduled
            // again.
            SinglesColumn.markDirtyAt(level, pos.below());
            SinglesColumn.markDirtyAt(level, pos.above());
        }
    }

    @Override
    public BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(
                HORIZONTAL_FACING, rotation.rotate(state.getValue(HORIZONTAL_FACING)));
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
}
