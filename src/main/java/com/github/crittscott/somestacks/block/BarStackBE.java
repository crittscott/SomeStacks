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
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
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

public class BarStackBE extends BlockEntity {
    private VoxelShape cachedShape = null;

    private final ItemStackHandler items = new ItemStackHandler(64) {
        @Override
        protected void onContentsChanged(int slot) {
            setChanged();
            cachedShape = null;
            if (level != null && !level.isClientSide) {
                syncToClients();

                int newLight = ItemOps.calculateLightLevelFromItems(this);
                BlockState state = getBlockState();
                int currentLight = state.getValue(BarStackBlock.LIGHT_LEVEL);
                if (newLight != currentLight) {
                    level.setBlock(getBlockPos(), state.setValue(BarStackBlock.LIGHT_LEVEL, newLight), Block.UPDATE_ALL);
                }
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
    private final LazyOptional<IItemHandler> itemsCap = LazyOptional.of(() -> items);

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

        if (!BarCubeIdx.isGrounded(index, items)) {
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

        ItemStack extracted = items.extractItem(index, 1, false);

        if (!extracted.isEmpty()) {
            removeUnsupportedBlocks();
        }

        return extracted;
    }

    private void removeUnsupportedBlocks() {
        boolean anyRemoved;
        do {
            anyRemoved = false;
            for (int i = 0; i < 64; i++) {
                if (!items.getStackInSlot(i).isEmpty()) {
                    if (!BarCubeIdx.isGrounded(i, items)) {
                        ItemStack removed = items.extractItem(i, 1, false);
                        if (!removed.isEmpty() && level != null && !level.isClientSide) {
                            net.minecraft.world.Containers.dropItemStack(level,
                                    getBlockPos().getX() + 0.5,
                                    getBlockPos().getY() + 0.5,
                                    getBlockPos().getZ() + 0.5,
                                    removed);
                            anyRemoved = true;
                        }
                    }
                }
            }
        } while (anyRemoved);
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

    @Override
    public void setRemoved() {
        super.setRemoved();
        itemsCap.invalidate();
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
