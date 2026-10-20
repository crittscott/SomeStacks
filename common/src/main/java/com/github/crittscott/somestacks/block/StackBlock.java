package com.github.crittscott.somestacks.block;

import com.github.crittscott.somestacks.server.StackInteractions;
import com.github.crittscott.somestacks.util.BlockType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.SimpleWaterloggedBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.level.lighting.LightEngine;
import net.minecraft.world.phys.BlockHitResult;

/** Block-state and interaction plumbing shared by all three stack blocks. */
public abstract class StackBlock extends Block implements EntityBlock, SimpleWaterloggedBlock {
    public static final IntegerProperty LIGHT_LEVEL =
            IntegerProperty.create("light", 0, LightEngine.MAX_LEVEL);
    public static final BooleanProperty WATERLOGGED = BlockStateProperties.WATERLOGGED;
    public static final EnumProperty<Direction> HORIZONTAL_FACING =
            BlockStateProperties.HORIZONTAL_FACING;

    protected StackBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState()
                .setValue(LIGHT_LEVEL, 0)
                .setValue(WATERLOGGED, false));
        if (type() != BlockType.BAR_STACK) {
            registerDefaultState(defaultBlockState().setValue(HORIZONTAL_FACING, Direction.NORTH));
        }
    }

    /** State shared by player placement and automatic growth, including Storage facing inheritance. */
    public static BlockState placementState(Block block, LevelReader level, BlockPos pos) {
        BlockState state = block.defaultBlockState().setValue(WATERLOGGED,
                level.getFluidState(pos).getType() == Fluids.WATER);
        if (block instanceof StackBlock stack && stack.type() == BlockType.STORAGE_STACK) {
            StorageStackBE neighbour = level.getBlockEntity(pos.below()) instanceof StorageStackBE below
                    ? below : level.getBlockEntity(pos.above()) instanceof StorageStackBE above ? above : null;
            if (neighbour != null) {
                state = state.setValue(HORIZONTAL_FACING,
                        neighbour.getBlockState().getValue(HORIZONTAL_FACING));
            }
        }
        return state;
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return placementState(this, context.getLevel(), context.getClickedPos());
    }

    /** The stack behavior registered by this block. */
    public abstract BlockType type();

    /** Shared properties for the Bar Stack's dynamic occupied shape. */
    public static BlockBehaviour.Properties barProperties() {
        return commonProperties(MapColor.METAL).dynamicShape();
    }

    /** Shared properties for the Singles Stack's dynamic occupied shape. */
    public static BlockBehaviour.Properties singlesProperties() {
        return commonProperties(MapColor.WOOD).dynamicShape();
    }

    /** Shared properties for the Storage Stack's full-block shape. */
    public static BlockBehaviour.Properties storageProperties() {
        return commonProperties(MapColor.METAL);
    }

    private static BlockBehaviour.Properties commonProperties(MapColor mapColor) {
        return BlockBehaviour.Properties.of()
                .mapColor(mapColor)
                .noOcclusion()
                .pushReaction(PushReaction.IMMOVEABLE)
                .strength(0.5F, 6.0F)
                .lightLevel(state -> state.getValue(LIGHT_LEVEL));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(LIGHT_LEVEL, WATERLOGGED);
        if (type() != BlockType.BAR_STACK) builder.add(HORIZONTAL_FACING);
    }

    @Override
    public BlockState rotate(BlockState state, Rotation rotation) {
        return state.hasProperty(HORIZONTAL_FACING)
                ? state.setValue(HORIZONTAL_FACING, rotation.rotate(state.getValue(HORIZONTAL_FACING))) : state;
    }

    @Override
    public BlockState mirror(BlockState state, Mirror mirror) {
        return state.hasProperty(HORIZONTAL_FACING)
                ? rotate(state, mirror.getRotation(state.getValue(HORIZONTAL_FACING))) : state;
    }

    private StackRun<?> runAt(Level level, BlockPos pos) {
        return level.getBlockState(pos).is(this)
                && level.getBlockEntity(pos) instanceof StackBlockEntity member
                && member.itemRun() instanceof StackRun<?> run ? run : null;
    }

    @Override
    public int getAnalogOutputSignal(BlockState state, Level level, BlockPos pos, Direction direction) {
        StackRun<?> run = runAt(level, pos);
        return run == null ? 0 : run.comparatorSignal();
    }

    @Override
    public void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        StackRun<?> run = runAt(level, pos);
        if (run != null) tickRun(run, pos);
    }

    protected void tickRun(StackRun<?> run, BlockPos tickPos) { run.publishPending(); }

    @Override
    public void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean moving) {
        super.onPlace(state, level, pos, oldState, moving);
        if (!oldState.is(this)) {
            StackRun.invalidateAround(level, pos, this);
            afterRunAdded(level, pos);
        }
    }

    protected void afterRunAdded(Level level, BlockPos pos) {
        StackRun.publishAround(level, pos, this::runAt);
    }

    @Override
    public void affectNeighborsAfterRemoval(BlockState state, ServerLevel level, BlockPos pos, boolean moving) {
        if (level.getBlockState(pos).is(this)) return;
        StackRun.invalidateAround(level, pos, this);
        super.affectNeighborsAfterRemoval(state, level, pos, moving);
        afterRunRemoved(level, pos);
        StackRun.markDirtyAt(level, pos.below(), this::runAt);
        StackRun.markDirtyAt(level, pos.above(), this::runAt);
    }

    protected void afterRunRemoved(ServerLevel level, BlockPos pos) {
        StackRun.publishAround(level, pos, this::runAt);
    }

    @Override
    public FluidState getFluidState(BlockState state) {
        return state.getValue(WATERLOGGED)
                ? Fluids.WATER.getSource(false)
                : super.getFluidState(state);
    }

    @Override
    public BlockState updateShape(BlockState state, LevelReader level, ScheduledTickAccess ticks,
                                  BlockPos pos, Direction direction, BlockPos neighborPos,
                                  BlockState neighborState, RandomSource random) {
        if (state.getValue(WATERLOGGED)) {
            ticks.scheduleTick(pos, Fluids.WATER, Fluids.WATER.getTickDelay(level));
        }
        return super.updateShape(
                state, level, ticks, pos, direction, neighborPos, neighborState, random);
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.INVISIBLE;
    }

    @Override
    public InteractionResult useWithoutItem(
            BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!level.isClientSide() && StackInteractions.handleExistingStack(
                (ServerPlayer) player, InteractionHand.MAIN_HAND, hit, type())) {
            return InteractionResult.CONSUME;
        }
        // Client callbacks consume recognized gestures before native block use.
        return InteractionResult.PASS;
    }

    @Override
    public InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
                                       Player player, InteractionHand hand, BlockHitResult hit) {
        if (!level.isClientSide() && StackInteractions.handleExistingStack(
                (ServerPlayer) player, hand, hit, type())) {
            return InteractionResult.CONSUME;
        }
        return InteractionResult.PASS;
    }

    @Override
    public boolean hasAnalogOutputSignal(BlockState state) {
        return true;
    }
}
