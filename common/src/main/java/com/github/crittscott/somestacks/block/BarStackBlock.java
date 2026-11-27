package com.github.crittscott.somestacks.block;

import com.github.crittscott.somestacks.util.BarCubeIdx;
import com.github.crittscott.somestacks.util.BlockType;
import com.github.crittscott.somestacks.util.ViewRay;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import javax.annotation.Nullable;

/**
 * The Bar Stack block: the world-facing half of {@link BarStackBE}, holding the block state, the
 * shapes, and the breaking behavior.
 *
 * <p>Its outline and collision follow the occupied bars, so an empty block shows no highlight box
 * while remaining clickable through a full-block interaction shape. Pathfinding always treats the
 * block as obstructed, while its empty internal space causes no suffocation or camera fog.
 *
 * <p>Removing one of these takes the support out from under the bars above, so breaking collapses
 * the column onto the gap. It also carries the deferred publication tick that content edits
 * schedule on the bottom block of the column.
 */
public class BarStackBlock extends StackBlock {
    private static final VoxelShape FULL_BLOCK_SHAPE = Shapes.block();

    public BarStackBlock(Properties props) {
        super(props, BlockType.BAR_STACK);
    }

    /** Shared mining, lighting, collision, and piston settings for this stack type. */
    public static Properties properties() {
        return properties(MapColor.METAL).dynamicShape();
    }

    /**
     * The one bar the deposit would place: the target along the ray in a new block, provided it is
     * supported, which in an empty block means a bottom position over an occupied seam or the
     * world.
     */
    @Nullable
    @Override
    public VoxelShape firstDepositShape(Level level, BlockPos pos, BlockState state, ViewRay ray) {
        int index = ray.depositTarget(pos, BarStackBE.SLOTS, BarCubeIdx::localBox, null);
        return index >= 0
                        && BarCubeIdx.freshBlockSupports(index, BarStackBE.seamBeneath(level, pos))
                ? BarCubeIdx.shapeFor(index)
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
        if (be instanceof BarStackBE barBe) {
            return barBe.getCachedShape();
        }
        return Shapes.empty();
    }

    /**
     * Returns no visual occlusion, so empty space inside a partly filled block neither suffocates a
     * player nor fogs the camera. The bars remain solid through the collision shape.
     */
    @Override
    public VoxelShape getVisualShape(
            BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return Shapes.empty();
    }

    /**
     * Prevents pathfinding through the block. The dynamic collision shape never fills the cube, so
     * the inherited result would route mobs through occupied bar positions. This deliberately
     * treats an empty Bar Stack as blocked as well.
     */
    @Override
    public boolean isPathfindable(
            BlockState state, BlockGetter level, BlockPos pos, PathComputationType type) {
        return false;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new BarStackBE(pos, state);
    }

    /** Invalidates cached columns and schedules publication after this block joins a run. */
    @Override
    public void onPlace(
            BlockState state, Level level, BlockPos pos, BlockState oldState, boolean isMoving) {
        super.onPlace(state, level, pos, oldState, isMoving);
        if (!oldState.is(this)) {
            BarColumn.invalidateAround(level, pos);
            BarColumn.publishAround(level, pos);
        }
    }

    @Override
    public void onRemove(
            BlockState state, Level level, BlockPos pos, BlockState newState, boolean isMoving) {
        if (!state.is(newState.getBlock())) {
            boolean cascading = false;
            ServerPlayer breaker = null;
            BarStackBE.DropBatch drops = new BarStackBE.DropBatch();

            BlockEntity be = level.getBlockEntity(pos);
            if (be instanceof BarStackBE barBe) {
                cascading = barBe.wasRemovedByCascade();
                breaker = barBe.takeBreakingPlayer();
                drops.addAll(pos, barBe.getItems());
            }
            super.onRemove(state, level, pos, newState, isMoving);
            BarColumn.invalidateAround(level, pos);

            // Removing a block removes the supporting seam beneath the column above. A cascade
            // already walks upward itself, so only an external removal starts another collapse,
            // charged to the player whose break removed the block, if one did.
            if (!isMoving && !cascading) {
                BarStackBE.collapseAbove(level, pos, drops, breaker);
            }

            // Publish after collapse so surviving runs report their final contents.
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

    /**
     * Publishes deferred content, lighting, and comparator changes. Edits schedule this tick on the
     * column's bottom block, coalescing a burst of changes anywhere in the run into one pass.
     */
    @Override
    public void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        BarColumn column = BarColumn.at(level, pos);
        if (column != null) {
            column.publishPending();
        }
    }
}
