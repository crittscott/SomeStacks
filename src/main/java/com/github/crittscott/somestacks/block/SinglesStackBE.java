package com.github.crittscott.somestacks.block;

import com.github.crittscott.somestacks.ModRegistry;
import com.github.crittscott.somestacks.util.ItemOps;
import com.github.crittscott.somestacks.util.SinglesCubeIdx;
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

public class SinglesStackBE extends BlockEntity {
    private VoxelShape cachedShape = null;
    private int rotation = 0;
    private int[] cubeRotations = new int[64];

    private final ItemStackHandler items = new ItemStackHandler(64) {
        @Override
        protected void onContentsChanged(int slot) {
            setChanged();
            cachedShape = null;
            if (level != null && !level.isClientSide) {
                syncToClients();

                int newLight = ItemOps.calculateLightLevelFromItems(this);
                BlockState state = getBlockState();
                int currentLight = state.getValue(SinglesStackBlock.LIGHT_LEVEL);
                if (newLight != currentLight) {
                    level.setBlock(getBlockPos(), state.setValue(SinglesStackBlock.LIGHT_LEVEL, newLight), Block.UPDATE_ALL);
                }
            }
        }

        @Override
        public int getSlotLimit(int slot) {
            return 1;
        }

        @Override
        public boolean isItemValid(int slot, @Nonnull ItemStack stack) {
            return isValidSinglesItem(stack);
        }
    };
    private final LazyOptional<IItemHandler> itemsCap = LazyOptional.of(() -> items);

    public SinglesStackBE(BlockPos pos, BlockState state) {
        super(ModRegistry.SINGLES_STACK_BE.get(), pos, state);
    }

    public static boolean isValidSinglesItem(ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        }
        if (ItemOps.isItemFromDisabledMod(stack)) {
            return false;
        }
        return !BarStackBE.isValidBarItem(stack);
    }

    public int getRotation() {
        return rotation;
    }

    public void setRotation(int rotation) {
        this.rotation = rotation % 4;
        setChanged();
        cachedShape = null;
        syncToClients();
    }

    public int getCubeRotation(int index) {
        if (index < 0 || index >= 64) {
            return 0;
        }
        return cubeRotations[index];
    }

    public void setCubeRotation(int index, int cubeRot) {
        if (index < 0 || index >= 64) {
            return;
        }
        cubeRotations[index] = cubeRot % 4;
        setChanged();
        syncToClients();
    }

    public VoxelShape computeShape() {
        VoxelShape shape = Shapes.empty();
        int blockRotation = this.rotation;

        for (int i = 0; i < items.getSlots(); i++) {
            ItemStack stack = items.getStackInSlot(i);
            if (!stack.isEmpty()) {
                int[] storageXYZ = SinglesCubeIdx.xyzFromIndex(i);
                int[] visualXYZ = SinglesCubeIdx.rotateXYZ(storageXYZ[0], storageXYZ[1], storageXYZ[2], blockRotation);

                double minX = SinglesCubeIdx.startPixel(visualXYZ[0]) / 16.0;
                double minY = SinglesCubeIdx.startPixel(visualXYZ[1]) / 16.0;
                double minZ = SinglesCubeIdx.startPixel(visualXYZ[2]) / 16.0;
                double maxX = minX + 4.0 / 16.0;
                double maxY = minY + 4.0 / 16.0;
                double maxZ = minZ + 4.0 / 16.0;

                VoxelShape cubeShape = Shapes.box(minX, minY, minZ, maxX, maxY, maxZ);
                shape = Shapes.or(shape, cubeShape);
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

        if (!SinglesCubeIdx.isGrounded(index, items)) {
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
            cascadeUnsupportedBlocks(index);
        }

        return extracted;
    }

    private void cascadeUnsupportedBlocks(int removedIndex) {
        int[] xyz = SinglesCubeIdx.xyzFromIndex(removedIndex);
        int x = xyz[0];
        int z = xyz[2];
        int y = xyz[1];

        for (int checkY = y + 1; checkY < 4; checkY++) {
            int sourceIndex = checkY * 16 + z * 4 + x;

            if (!items.getStackInSlot(sourceIndex).isEmpty()) {
                int targetIndex = (checkY - 1) * 16 + z * 4 + x;

                ItemStack extracted = items.extractItem(sourceIndex, 1, false);
                items.insertItem(targetIndex, extracted, false);

                cubeRotations[targetIndex] = cubeRotations[sourceIndex];
                cubeRotations[sourceIndex] = 0;
            }
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

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        if (tag.contains("Items")) {
            items.deserializeNBT(tag.getCompound("Items"));
        }
        if (tag.contains("Rotation")) {
            rotation = tag.getInt("Rotation");
        }
        if (tag.contains("CubeRotations")) {
            int[] loaded = tag.getIntArray("CubeRotations");
            if (loaded.length == 64) {
                cubeRotations = loaded;
            }
        }
        cachedShape = null;
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.put("Items", items.serializeNBT());
        tag.putInt("Rotation", rotation);
        tag.putIntArray("CubeRotations", cubeRotations);
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
