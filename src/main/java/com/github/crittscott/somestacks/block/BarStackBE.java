package com.github.crittscott.somestacks.block;

import com.github.crittscott.somestacks.ModRegistry;
import com.github.crittscott.somestacks.ModTags;
import com.github.crittscott.somestacks.util.BarCubeIdx;
import com.github.crittscott.somestacks.util.ItemOps;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.Containers;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemStackHandler;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import java.util.Arrays;

public class BarStackBE extends BlockEntity {
    private VoxelShape cachedShape = null;
    private boolean suppressSync = false;

    private final ItemStackHandler items = new ItemStackHandler(64) {
        @Override
        protected void onContentsChanged(int slot) {
            setChanged();
            cachedShape = null;
            if (!suppressSync) {
                finalizeAfterBatch();
            }
        }

        @Override
        public int getSlotLimit(int slot) {
            return 1;
        }

        @Override
        public boolean isItemValid(int slot, @Nonnull ItemStack stack) {
            return isValidBarItem(stack);
        }
    };
    private LazyOptional<IItemHandler> itemsCap = LazyOptional.of(() -> items);

    public BarStackBE(BlockPos pos, BlockState state) {
        super(ModRegistry.BAR_STACK_BE.get(), pos, state);
    }

    public static boolean isValidBarItem(ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        }
        if (ItemOps.isItemFromDisabledMod(stack)) {
            return false;
        }
        return stack.is(ModTags.FORGE_INGOTS);
    }

    public VoxelShape computeShape() {
        VoxelShape shape = Shapes.empty();

        for (int i = 0; i < items.getSlots(); i++) {
            ItemStack stack = items.getStackInSlot(i);
            if (!stack.isEmpty()) {
                int[] xyz = BarCubeIdx.xyzFromIndex(i);

                double minX = BarCubeIdx.startPixelX(xyz[0], xyz[1]) / 16.0;
                double minY = BarCubeIdx.startPixelY(xyz[1]) / 16.0;
                double minZ = BarCubeIdx.startPixelZ(xyz[2], xyz[1]) / 16.0;
                double maxX = minX + BarCubeIdx.barWidth(xyz[1]) / 16.0;
                double maxY = minY + BarCubeIdx.barHeight(xyz[1]) / 16.0;
                double maxZ = minZ + BarCubeIdx.barDepth(xyz[1]) / 16.0;

                VoxelShape barShape = Shapes.box(minX, minY, minZ, maxX, maxY, maxZ);
                shape = Shapes.or(shape, barShape);
            }
        }

        return shape;
    }

    public VoxelShape getCachedShape() {
        if (cachedShape == null) {
            cachedShape = computeShape();
        }
        return cachedShape;
    }

    public boolean depositAt(int index, ItemStack fromHand) {
        if (fromHand.isEmpty()) {
            return false;
        }

        if (index < 0 || index >= 64) {
            return false;
        }

        if (!items.getStackInSlot(index).isEmpty()) {
            return false;
        }

        if (!isValidBarItem(fromHand)) {
            return false;
        }

        if (!BarCubeIdx.isGrounded(index, items, seamBeneath())) {
            return false;
        }

        ItemStack toInsert = fromHand.copy();
        toInsert.setCount(1);
        ItemStack remainder = items.insertItem(index, toInsert, false);

        if (remainder.isEmpty()) {
            fromHand.shrink(1);
            return true;
        }

        return false;
    }

    public ItemStack extractAt(int index) {
        if (index < 0 || index >= 64) {
            return ItemStack.EMPTY;
        }

        boolean[] topBefore = BarCubeIdx.topLayerOccupancy(items);

        ItemStack extracted;
        suppressSync = true;
        try {
            extracted = items.extractItem(index, 1, false);
        } finally {
            suppressSync = false;
        }

        if (extracted.isEmpty()) {
            return ItemStack.EMPTY;
        }

        if (level == null || level.isClientSide) {
            setChanged();
            return extracted;
        }

        settleColumn(seamBeneath(), topBefore);
        return extracted;
    }

    /**
     * The support this block's bottom layer rests on: the top-layer occupancy of the Bar Stack
     * directly below, or null when this block stands on the world instead of on another Bar Stack.
     */
    private boolean[] seamBeneath() {
        if (level != null && level.getBlockEntity(getBlockPos().below()) instanceof BarStackBE below) {
            return BarCubeIdx.topLayerOccupancy(below.items);
        }
        return null;
    }

    /**
     * Settles this block against the seam beneath it, then carries the result up the column so a
     * vertical run of Bar Stacks behaves as one stack. Support crosses the seam per footprint, so a
     * block above loses only the bars whose support went away rather than collapsing wholesale. Only
     * the top layer can hold up the block above, so a block whose top layer survives intact ends the
     * walk. A block emptied along the way removes itself and passes on its now-empty top layer,
     * which is why an emptied or vanished block needs no case of its own: nothing overlaps an empty
     * seam. {@code topBefore} is this block's top layer as it stood before the edit that prompted
     * the settle, which the edit itself may already have changed.
     */
    private void settleColumn(boolean[] seamBelow, boolean[] topBefore) {
        Level columnLevel = level;
        if (columnLevel == null) {
            return;
        }

        BarStackBE be = this;
        boolean[] seam = seamBelow;
        boolean[] before = topBefore;

        while (true) {
            be.dropUnsupported(columnLevel, seam);
            boolean[] after = BarCubeIdx.topLayerOccupancy(be.items);

            BlockPos abovePos = be.getBlockPos().above();

            if (be.isEmpty()) {
                columnLevel.setBlock(be.getBlockPos(), Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
            } else {
                be.setChanged();
                be.finalizeAfterBatch();
            }

            if (Arrays.equals(before, after)) {
                return;
            }

            if (!(columnLevel.getBlockEntity(abovePos) instanceof BarStackBE above)) {
                return;
            }

            be = above;
            seam = after;
            before = BarCubeIdx.topLayerOccupancy(above.items);
        }
    }

    /**
     * Drops every bar this block leaves without support. Slot index is layer-major and a bar is
     * supported only by the layer directly beneath it, so one ascending pass settles the block: by
     * the time a layer is reached, the layer it rests on is final, whether that is the layer below
     * it here or the seam.
     */
    private void dropUnsupported(Level columnLevel, boolean[] seamBelow) {
        suppressSync = true;
        try {
            for (int i = 0; i < 64; i++) {
                if (items.getStackInSlot(i).isEmpty() || BarCubeIdx.isGrounded(i, items, seamBelow)) {
                    continue;
                }

                ItemStack removed = items.extractItem(i, 1, false);
                if (!removed.isEmpty()) {
                    Containers.dropItemStack(columnLevel,
                            getBlockPos().getX() + 0.5,
                            getBlockPos().getY() + 0.5,
                            getBlockPos().getZ() + 0.5,
                            removed);
                }
            }
        } finally {
            suppressSync = false;
        }
    }

    public boolean isEmpty() {
        return ItemOps.isHandlerEmpty(items);
    }

    public void syncToClients() {
        if (level != null) {
            BlockState state = getBlockState();
            level.sendBlockUpdated(getBlockPos(), state, state, Block.UPDATE_ALL);
        }
    }

    /**
     * Settles the derived state a content edit leaves behind: pushes contents to clients and
     * recomputes the emitted light level. Callers mark the block changed; this reproduces the rest
     * of the per-edit path as one pass, so a batch that suppresses per-slot sync can finalize once
     * when it finishes.
     */
    private void finalizeAfterBatch() {
        if (level == null || level.isClientSide) {
            return;
        }
        syncToClients();

        int newLight = ItemOps.calculateLightLevelFromItems(items);
        BlockState state = getBlockState();
        if (state.getValue(BarStackBlock.LIGHT_LEVEL) != newLight) {
            level.setBlock(getBlockPos(), state.setValue(BarStackBlock.LIGHT_LEVEL, newLight), Block.UPDATE_ALL);
        }
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        if (tag.contains("Items")) {
            items.deserializeNBT(tag.getCompound("Items"));
        }
        cachedShape = null;
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.put("Items", items.serializeNBT());
    }

    @Nonnull
    @Override
    public <T> LazyOptional<T> getCapability(@Nonnull Capability<T> cap, @Nullable Direction side) {
        if (cap == ForgeCapabilities.ITEM_HANDLER) return itemsCap.cast();
        return super.getCapability(cap, side);
    }

    /** This block's 64 slots, for callers that already hold the block entity. */
    public IItemHandler getItems() {
        return items;
    }

    @Override
    public void invalidateCaps() {
        super.invalidateCaps();
        itemsCap.invalidate();
    }

    @Override
    public void reviveCaps() {
        super.reviveCaps();
        itemsCap = LazyOptional.of(() -> items);
    }

    @Override
    public ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public CompoundTag getUpdateTag() {
        CompoundTag tag = new CompoundTag();
        saveAdditional(tag);
        return tag;
    }

    @Override
    public void onDataPacket(Connection net, ClientboundBlockEntityDataPacket pkt) {
        handleUpdateTag(pkt.getTag());
    }
}
