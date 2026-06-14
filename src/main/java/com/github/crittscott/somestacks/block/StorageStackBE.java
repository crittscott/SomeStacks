package com.github.crittscott.somestacks.block;

import com.github.crittscott.somestacks.ModRegistry;
import com.github.crittscott.somestacks.ServerConfig;
import com.github.crittscott.somestacks.util.ItemOps;
import com.github.crittscott.somestacks.util.StackSort;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemStackHandler;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

public class StorageStackBE extends BlockEntity {
    private boolean suppressSync = false;

    private final ItemStackHandler items = new ItemStackHandler(27) {
        @Override
        protected void onContentsChanged(int slot) {
            setChanged();
            if (level != null && !level.isClientSide && !suppressSync) {
                syncToClients();
                level.updateNeighborsAt(getBlockPos(), getBlockState().getBlock());

                int newLight = ItemOps.calculateLightLevelFromItems(this);
                BlockState state = getBlockState();
                int currentLight = state.getValue(StorageStackBlock.LIGHT_LEVEL);
                if (newLight != currentLight) {
                    level.setBlock(getBlockPos(), state.setValue(StorageStackBlock.LIGHT_LEVEL, newLight), Block.UPDATE_ALL);
                }
            }
        }

        @Override
        public boolean isItemValid(int slot, @Nonnull ItemStack stack) {
            return isValidStorageItem(stack);
        }
    };
    private final LazyOptional<IItemHandler> itemsCap = LazyOptional.of(() -> new PileItemHandler(this));

    private int rotation = 0;
    private long lastSortTime = 0L;
    private boolean permanent = false;

    public StorageStackBE(BlockPos pos, BlockState state) {
        super(ModRegistry.STACK_BE.get(), pos, state);
    }

    public static boolean isValidStorageItem(ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        }
        if (ItemOps.isItemFromDisabledMod(stack)) {
            return false;
        }
        return true;
    }

    /**
     * Direct slot access for PileAwareItemHandler only.
     */
    ItemStack getSlotDirect(int slot) {
        return items.getStackInSlot(slot);
    }

    public int getRotation() {
        return rotation;
    }

    public void setRotation(int rotation) {
        this.rotation = rotation % 4;
        setChanged();
        syncToClients();
    }

    public long getLastSortTime() {
        return lastSortTime;
    }

    public void setLastSortTime(long time) {
        this.lastSortTime = time;
        setChanged();
    }

    public boolean isPermanent() {
        return permanent;
    }

    public void setPermanent(boolean permanent) {
        this.permanent = permanent;
        setChanged();
        syncToClients();
    }

    public double calculateFillLevel() {
        double sum = 0.0;
        for (int i = 0; i < items.getSlots(); i++) {
            ItemStack stack = items.getStackInSlot(i);
            if (!stack.isEmpty()) {
                sum += (double) stack.getCount() / stack.getMaxStackSize();
            }
        }
        return sum / 27.0;
    }

    public int deposit(ItemStack fromHand) {
        if (fromHand.isEmpty()) {
            return 0;
        }

        int moved = mergeIntoHandler(items, fromHand);

        if (!fromHand.isEmpty() && level != null && !level.isClientSide) {
            BlockPos above = getBlockPos().above();
            BlockState aboveState = level.getBlockState(above);

            if (aboveState.getBlock() == ModRegistry.STORAGE_STACK_BLOCK.get()) {
                var beAbove = level.getBlockEntity(above);
                if (beAbove instanceof StorageStackBE sbeAbove) {
                    int movedAbove = sbeAbove.deposit(fromHand);
                    moved += movedAbove;
                }
            } else if (aboveState.canBeReplaced() && ServerConfig.ENABLE_STORAGE_STACK_BLOCK.get()) {
                BlockState newStack = ModRegistry.STORAGE_STACK_BLOCK.get().defaultBlockState();
                if (level.setBlock(above, newStack, Block.UPDATE_ALL)) {
                    var beAbove = level.getBlockEntity(above);
                    if (beAbove instanceof StorageStackBE sbeAbove) {
                        int movedAbove = sbeAbove.deposit(fromHand);
                        moved += movedAbove;
                    }
                }
            }
        }

        if (moved > 0 && level != null && !level.isClientSide) {
            resortAndPackPile();
        }

        return moved;
    }

    public ItemStack extractAt(int index, int maxCount, @Nullable ItemStack playerHand) {
        if (index < 0 || index >= items.getSlots()) {
            return ItemStack.EMPTY;
        }

        ItemStack inSlot = items.getStackInSlot(index);
        if (inSlot.isEmpty()) {
            return ItemStack.EMPTY;
        }

        if (!ItemOps.canTakeIntoHand(playerHand, inSlot)) {
            return ItemStack.EMPTY;
        }

        int toTake = Math.min(maxCount, inSlot.getCount());
        ItemStack taken = inSlot.copy();
        taken.setCount(toTake);
        items.extractItem(index, toTake, false);

        if (level != null && !level.isClientSide) {
            resortAndPackPile();
        }

        return taken;
    }

    public ItemStack extractFromSlot(int slot, int amount) {
        if (slot < 0 || slot >= items.getSlots()) {
            return ItemStack.EMPTY;
        }

        ItemStack extracted = items.extractItem(slot, amount, false);

        if (!extracted.isEmpty() && level != null && !level.isClientSide) {
            resortAndPackPile();
        }

        return extracted;
    }

    public boolean isEmpty() {
        return ItemOps.isHandlerEmpty(items);
    }

    public void syncToClients() {
        if (level != null) {
            BlockState state = getBlockState();
            level.sendBlockUpdated(getBlockPos(), state, state, 3);
        }
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        if (tag.contains("Items")) items.deserializeNBT(tag.getCompound("Items"));
        if (tag.contains("Rotation")) rotation = tag.getInt("Rotation");
        if (tag.contains("LastSortTime")) lastSortTime = tag.getLong("LastSortTime");
        if (tag.contains("Permanent")) permanent = tag.getBoolean("Permanent");
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.put("Items", items.serializeNBT());
        tag.putInt("Rotation", rotation);
        tag.putLong("LastSortTime", lastSortTime);
        tag.putBoolean("Permanent", permanent);
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

    // Pile Management

    private BlockPos findPileBase() {
        BlockPos current = getBlockPos();
        while (true) {
            BlockPos below = current.below();
            if (level.getBlockState(below).getBlock() == ModRegistry.STORAGE_STACK_BLOCK.get()) {
                current = below;
            } else {
                break;
            }
        }
        return current;
    }

    private List<StorageStackBE> collectPileStacksLimited(BlockPos origin, int maxStacks) {
        List<StorageStackBE> downward = new ArrayList<>();
        List<StorageStackBE> upward = new ArrayList<>();

        BlockPos current = origin;
        while (downward.size() < maxStacks && level.getBlockState(current).getBlock() == ModRegistry.STORAGE_STACK_BLOCK.get()) {
            var be = level.getBlockEntity(current);
            if (be instanceof StorageStackBE sbe) {
                downward.add(0, sbe);
            } else {
                break;
            }
            current = current.below();
        }

        int remaining = maxStacks - downward.size();
        current = origin.above();
        while (remaining > 0 && level.getBlockState(current).getBlock() == ModRegistry.STORAGE_STACK_BLOCK.get()) {
            var be = level.getBlockEntity(current);
            if (be instanceof StorageStackBE sbe) {
                upward.add(sbe);
                remaining--;
            } else {
                break;
            }
            current = current.above();
        }

        List<StorageStackBE> result = new ArrayList<>(downward);
        result.addAll(upward);

        return result;
    }

    public void resortAndPackPile() {
        if (level.isClientSide) return;

        BlockPos base = findPileBase();

        var baseEntity = level.getBlockEntity(base);
        if (baseEntity instanceof StorageStackBE baseSbe) {
            long currentTime = level.getGameTime();
            long lastSort = baseSbe.getLastSortTime();
            long elapsed = currentTime - lastSort;

            int cooldownTicks = ServerConfig.PILE_SORT_COOLDOWN_TICKS.get();
            if (elapsed < cooldownTicks) {
                return;
            }

            baseSbe.setLastSortTime(currentTime);
        }

        int maxStacks = ServerConfig.PILE_SORT_MAX_STACKS.get();
        List<StorageStackBE> pileStacks = collectPileStacksLimited(getBlockPos(), maxStacks);

        for (StorageStackBE sbe : pileStacks) {
            sbe.suppressSync = true;
        }

        try {
            final List<ItemStack> allItems = new ArrayList<>();
            for (StorageStackBE sbe : pileStacks) {
                for (int i = 0; i < sbe.items.getSlots(); i++) {
                    ItemStack stack = sbe.items.getStackInSlot(i);
                    if (!stack.isEmpty()) {
                        allItems.add(stack.copy());
                    }
                }
            }

            allItems.sort(StackSort.COMPARATOR);
            List<ItemStack> consolidated = consolidate(allItems);

            for (StorageStackBE sbe : pileStacks) {
                for (int i = 0; i < sbe.items.getSlots(); i++) {
                    sbe.items.setStackInSlot(i, ItemStack.EMPTY);
                }
            }

            int itemIndex = 0;
            for (StorageStackBE sbe : pileStacks) {
                for (int slot = 0; slot < sbe.items.getSlots() && itemIndex < consolidated.size(); slot++) {
                    sbe.items.setStackInSlot(slot, consolidated.get(itemIndex));
                    itemIndex++;
                }
            }
        } finally {
            for (StorageStackBE sbe : pileStacks) {
                sbe.suppressSync = false;
                sbe.setChanged();
                sbe.syncToClients();
            }
        }

        for (int i = pileStacks.size() - 1; i >= 0; i--) {
            StorageStackBE sbe = pileStacks.get(i);
            if (sbe.isEmpty() && !sbe.isPermanent()) {
                BlockPos pos = sbe.getBlockPos();
                level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
            } else {
                break;
            }
        }
    }

    // Item Management Utilities

    private int mergeIntoHandler(IItemHandler handler, ItemStack from) {
        if (from.isEmpty()) return 0;
        int moved = 0;

        // Fill partials first
        for (int i = 0; i < handler.getSlots() && !from.isEmpty(); i++) {
            ItemStack slot = handler.getStackInSlot(i);
            if (canMerge(slot, from)) {
                int can = Math.min(from.getCount(), slot.getMaxStackSize() - slot.getCount());
                if (can > 0) {
                    ItemStack sim = slot.copy();
                    sim.grow(can);
                    handler.extractItem(i, 0, true); // no-op: ensure valid slot
                    handler.insertItem(i, from.copy().split(can), false);
                    from.shrink(can);
                    moved += can;
                }
            }
        }
        // Fill empties
        for (int i = 0; i < handler.getSlots() && !from.isEmpty(); i++) {
            ItemStack slot = handler.getStackInSlot(i);
            if (slot.isEmpty()) {
                ItemStack ins = from.copy();
                int put = Math.min(ins.getCount(), ins.getMaxStackSize());
                ins.setCount(put);
                ItemStack rem = handler.insertItem(i, ins, false);
                int used = put - rem.getCount();
                from.shrink(used);
                moved += used;
            }
        }
        return moved;
    }

    private boolean canMerge(ItemStack into, ItemStack from) {
        if (into.isEmpty() || from.isEmpty()) return false;
        if (!ItemStack.isSameItemSameTags(into, from)) return false;
        return into.getCount() < into.getMaxStackSize();
    }

    private List<ItemStack> consolidate(List<ItemStack> stacks) {
        stacks.removeIf(ItemStack::isEmpty);
        stacks.sort(StackSort.COMPARATOR);

        List<ItemStack> out = new ArrayList<>();

        for (ItemStack current : stacks) {
            if (current.isEmpty()) continue;

            if (out.isEmpty()) {
                // First item, just add it
                out.add(current.copy());
            } else {
                ItemStack last = out.get(out.size() - 1);

                if (canMerge(last, current)) {
                    // Merge as much as possible into the last stack
                    int space = last.getMaxStackSize() - last.getCount();
                    int toMerge = Math.min(current.getCount(), space);
                    last.grow(toMerge);
                    current.shrink(toMerge);

                    // If there's leftover, add it as a new stack
                    if (!current.isEmpty()) {
                        out.add(current.copy());
                    }
                } else {
                    // Different item type, add as new stack
                    out.add(current.copy());
                }
            }
        }

        return out;
    }
}
