package com.github.crittscott.somestacks.block;

import com.github.crittscott.somestacks.CommonRegistry;

import com.github.crittscott.somestacks.util.BlockType;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import javax.annotation.Nullable;

/**
 * The Storage Stack block: the world-facing half of {@link StorageStackBE}, holding the block
 * state, shapes, and breaking behavior. Unlike Singles and Bar, Storage keeps a full-block shape
 * regardless of which cells are occupied.
 *
 * <p>Breaking drops only this block's own contents. What that does to the rest of the pile, which
 * may need to settle or shrink around the gap, belongs to {@link StoragePile}.
 */
public class StorageStackBlock extends StackBlock {
    @Override
    public BlockType type() {
        return BlockType.STORAGE_STACK;
    }

    public StorageStackBlock(Properties props) {
        super(props);
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return CommonRegistry.storageStackBe().create(pos, state);
    }

    @Override
    public boolean propagatesSkylightDown(BlockState state) {
        return true;
    }

    @Override
    public float getShadeBrightness(BlockState state, BlockGetter level, BlockPos pos) {
        return 1.0F;
    }

    @Override
    public int getLightDampening(BlockState state) {
        return 0;
    }

    @Override
    protected void tickRun(StackRun<?> run, BlockPos tickPos) {
        if (run.basePos().equals(tickPos)) ((StoragePile) run).settle();
    }

    @Override
    protected void afterRunAdded(Level level, BlockPos pos) {
        if (!level.isClientSide() && level.getBlockEntity(pos) instanceof StorageStackBE placed) {
            StoragePile.adoptNeighbourState(level, pos, placed);
            StoragePile.markDirtyAt(level, pos);
        }
    }

    @Override
    protected void afterRunRemoved(ServerLevel level, BlockPos pos) {
        // Storage publishes only after its scheduled settlement and trim.
    }
}
