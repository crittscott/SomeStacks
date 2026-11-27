package com.github.crittscott.somestacks.block;

import com.github.crittscott.somestacks.server.StackInteractions;
import com.github.crittscott.somestacks.util.BlockType;
import com.github.crittscott.somestacks.util.ItemOps;
import com.github.crittscott.somestacks.util.ViewRay;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.SimpleWaterloggedBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.VoxelShape;

import javax.annotation.Nullable;

/**
 * The block half every stack type shares with its {@link StackBlockEntity}: emitted light,
 * waterlogging, entity rendering, mining and piston settings, the gesture click, the breaker record
 * that removal cleanup is charged to, and the run-wide comparator reading.
 *
 * <p>Each subclass owns its block entity, shapes, placement and removal hooks, and the publication
 * tick its edits schedule.
 */
public abstract class StackBlock extends Block implements EntityBlock, SimpleWaterloggedBlock {
    /**
     * Light this block emits, derived from the {@code BlockItem}s stored in it and kept in the
     * block state so lighting updates travel by the ordinary block-state path.
     */
    public static final IntegerProperty LIGHT_LEVEL =
            IntegerProperty.create("light", 0, ItemOps.MAX_LIGHT_LEVEL);

    public static final BooleanProperty WATERLOGGED = BlockStateProperties.WATERLOGGED;

    /** Mining time of every stack block. */
    private static final float DESTROY_TIME = 0.5F;

    /** Explosion resistance of every stack block. */
    private static final float EXPLOSION_RESISTANCE = 6.0F;

    private final BlockType type;

    /** Registers the shared default state; a subclass adds defaults for its own properties. */
    protected StackBlock(Properties props, BlockType type) {
        super(props);
        this.type = type;
        registerDefaultState(
                defaultBlockState().setValue(LIGHT_LEVEL, 0).setValue(WATERLOGGED, false));
    }

    /** Shared mining, lighting, collision, and piston settings, with this type's map color. */
    protected static Properties properties(MapColor color) {
        return Properties.of()
                .mapColor(color)
                .noOcclusion()
                .pushReaction(PushReaction.BLOCK)
                .strength(DESTROY_TIME, EXPLOSION_RESISTANCE)
                .lightLevel(state -> state.getValue(LIGHT_LEVEL));
    }

    /** Adds the shared properties; a subclass calls this and adds its own. */
    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(LIGHT_LEVEL, WATERLOGGED);
    }

    /**
     * Plans a player's first deposit into a block of this type not yet placed at {@code pos} in
     * {@code state}: the collision shape the block will have once it holds that deposit, or null
     * when a deposit along {@code ray} would find no supported cell in it. Changes nothing;
     * placement tests obstruction against this shape, since a new block starts empty and takes its
     * real shape only with its first deposit.
     */
    @Nullable
    public abstract VoxelShape firstDepositShape(
            Level level, BlockPos pos, BlockState state, ViewRay ray);

    /** Reports the whole run's fill, so a comparator reads the same value anywhere along it. */
    @Override
    public int getAnalogOutputSignal(BlockState state, Level level, BlockPos pos) {
        if (!(level.getBlockEntity(pos) instanceof StackBlockEntity stack)) {
            return 0;
        }
        StackRun<?> run = stack.itemRun();
        return run == null ? 0 : run.comparatorSignal();
    }

    @Override
    public FluidState getFluidState(BlockState state) {
        return state.getValue(WATERLOGGED)
                ? Fluids.WATER.getSource(false)
                : super.getFluidState(state);
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.ENTITYBLOCK_ANIMATED;
    }

    @Override
    public boolean hasAnalogOutputSignal(BlockState state) {
        return true;
    }

    /**
     * Records the breaking player before removal, so the cleanup a subclass's {@code onRemove}
     * starts removes blocks on that player's behalf rather than the automation identity's.
     */
    @Override
    public void playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        if (player instanceof ServerPlayer breaker
                && level.getBlockEntity(pos) instanceof StackBlockEntity stack) {
            stack.noteBreakingPlayer(breaker);
        }
        super.playerWillDestroy(level, pos, state, player);
    }

    @Override
    public BlockState updateShape(
            BlockState state,
            Direction direction,
            BlockState neighborState,
            LevelAccessor level,
            BlockPos pos,
            BlockPos neighborPos) {
        if (state.getValue(WATERLOGGED)) {
            level.scheduleTick(pos, Fluids.WATER, Fluids.WATER.getTickDelay(level));
        }
        return super.updateShape(state, direction, neighborState, level, pos, neighborPos);
    }

    @Override
    public InteractionResult use(
            BlockState state,
            Level level,
            BlockPos pos,
            Player player,
            InteractionHand hand,
            BlockHitResult hit) {
        if (!level.isClientSide) {
            StackInteractions.handleExistingStack((ServerPlayer) player, hand, hit, type);
        }
        return level.isClientSide ? InteractionResult.SUCCESS : InteractionResult.CONSUME;
    }
}
