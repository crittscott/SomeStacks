package com.github.crittscott.somestacks.block;

import com.github.crittscott.somestacks.ModRegistry;
import com.github.crittscott.somestacks.ServerConfig;
import com.github.crittscott.somestacks.server.Protection;
import com.github.crittscott.somestacks.util.ItemOps;
import com.github.crittscott.somestacks.util.StackSort;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemStackHandler;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class StorageStackBE extends BlockEntity {
    private boolean suppressSync = false;

    private final ItemStackHandler items = new ItemStackHandler(27) {
        @Override
        protected void onContentsChanged(int slot) {
            setChanged();
            if (!suppressSync) {
                finalizeAfterBatch();
            }
        }

        @Override
        public boolean isItemValid(int slot, @Nonnull ItemStack stack) {
            return isValidStorageItem(stack);
        }
    };
    private LazyOptional<IItemHandler> itemsCap = LazyOptional.of(() -> new PileItemHandler(this));

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
        return deposit(fromHand, true, null);
    }

    /**
     * @param placer the player responsible for any overflow blocks this deposit creates, or null
     *               for automation. A created block is placed through the protection-aware path,
     *               attributed to {@code placer} or, when null, to the level's fake player.
     */
    public int deposit(ItemStack fromHand, @Nullable ServerPlayer placer) {
        return deposit(fromHand, true, placer);
    }

    /**
     * @param ownsRepack whether this call is responsible for the post-deposit repack. Overflow
     *                   frames pass false, so a deposit that spills up a tall pile walks to the
     *                   pile base once instead of once per block it touched.
     * @param placer     see {@link #deposit(ItemStack, ServerPlayer)}.
     */
    private int deposit(ItemStack fromHand, boolean ownsRepack, @Nullable ServerPlayer placer) {
        if (fromHand.isEmpty()) {
            return 0;
        }

        if (!isValidStorageItem(fromHand)) {
            return 0;
        }

        int moved = mergeIntoHandler(items, fromHand);

        if (!fromHand.isEmpty() && level != null && !level.isClientSide) {
            BlockPos above = getBlockPos().above();
            BlockState aboveState = level.getBlockState(above);

            if (aboveState.getBlock() == ModRegistry.STORAGE_STACK_BLOCK.get()) {
                var beAbove = level.getBlockEntity(above);
                if (beAbove instanceof StorageStackBE sbeAbove) {
                    int movedAbove = sbeAbove.deposit(fromHand, false, placer);
                    moved += movedAbove;
                }
            } else if (aboveState.canBeReplaced() && ServerConfig.ENABLE_STORAGE_STACK_BLOCK.get()
                    && !level.isOutsideBuildHeight(above)) {
                ServerLevel serverLevel = (ServerLevel) level;
                // Automation carries no player; attribute its growth to the level's fake player so
                // the same protection path governs machine-driven overflow.
                ServerPlayer editor = placer != null ? placer : FakePlayerFactory.getMinecraft(serverLevel);
                BlockState newStack = ModRegistry.STORAGE_STACK_BLOCK.get().defaultBlockState();
                if (!Protection.isProtected(editor, above)
                        && Protection.placeChecked(editor, serverLevel, above, newStack, Direction.DOWN)) {
                    var beAbove = level.getBlockEntity(above);
                    if (beAbove instanceof StorageStackBE sbeAbove) {
                        int movedAbove = sbeAbove.deposit(fromHand, false, placer);
                        moved += movedAbove;
                    }
                }
            }
        }

        if (ownsRepack && moved > 0 && level != null && !level.isClientSide) {
            resortAndPackPile();
        }

        return moved;
    }

    /**
     * Read-only twin of {@link #deposit}: how many of {@code fromHand} that same call
     * would move, walking local slots and then overflowing up the pile exactly as
     * deposit does, without mutating anything. Kept beside deposit so the two stay in
     * step.
     */
    public int simulateDeposit(ItemStack fromHand) {
        if (fromHand.isEmpty()) {
            return 0;
        }
        if (!isValidStorageItem(fromHand)) {
            return 0;
        }
        return simulateAbsorb(fromHand.getCount(), fromHand);
    }

    private int simulateAbsorb(int count, ItemStack ref) {
        int remaining = count - fillLocally(count, ref);

        if (remaining > 0 && level != null && !level.isClientSide) {
            BlockPos above = getBlockPos().above();
            BlockState aboveState = level.getBlockState(above);

            if (aboveState.getBlock() == ModRegistry.STORAGE_STACK_BLOCK.get()) {
                if (level.getBlockEntity(above) instanceof StorageStackBE sbeAbove) {
                    remaining -= sbeAbove.simulateAbsorb(remaining, ref);
                }
            } else if (aboveState.canBeReplaced() && ServerConfig.ENABLE_STORAGE_STACK_BLOCK.get()
                    && !level.isOutsideBuildHeight(above)) {
                // deposit would create an empty block here and fill it, chaining further
                // up if needed, so it absorbs whatever is left.
                remaining = 0;
            }
        }

        return count - remaining;
    }

    private int fillLocally(int count, ItemStack ref) {
        int remaining = count;
        // Compatible partials first, then empty slots: the order mergeIntoHandler uses.
        for (int i = 0; i < items.getSlots() && remaining > 0; i++) {
            ItemStack slot = items.getStackInSlot(i);
            if (!slot.isEmpty() && ItemStack.isSameItemSameTags(slot, ref)) {
                remaining -= Math.min(remaining, slot.getMaxStackSize() - slot.getCount());
            }
        }
        for (int i = 0; i < items.getSlots() && remaining > 0; i++) {
            if (items.getStackInSlot(i).isEmpty()) {
                remaining -= Math.min(remaining, ref.getMaxStackSize());
            }
        }
        return count - remaining;
    }

    /**
     * Whether a deposit into this block could spill past its local slots: either a
     * Storage block already sits above, or the space above is replaceable, within build
     * height, and Storage creation is enabled. The pile handler uses this to advertise
     * overflow headroom to automation that gauges capacity by reading slots.
     */
    public boolean canOverflowUpward() {
        if (level == null || level.isClientSide) {
            return false;
        }
        BlockPos above = getBlockPos().above();
        if (level.isOutsideBuildHeight(above)) {
            return false;
        }
        BlockState aboveState = level.getBlockState(above);
        if (aboveState.getBlock() == ModRegistry.STORAGE_STACK_BLOCK.get()) {
            return true;
        }
        return aboveState.canBeReplaced() && ServerConfig.ENABLE_STORAGE_STACK_BLOCK.get();
    }

    /**
     * Whether a Storage Stack sits directly above this one. An empty block with a Storage block
     * above it must never be removed, or the pile above would be severed from its base.
     */
    public boolean hasStorageBlockAbove() {
        return level != null
                && level.getBlockState(getBlockPos().above()).is(ModRegistry.STORAGE_STACK_BLOCK.get());
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

    /**
     * Settles the derived state a content edit leaves behind: pushes contents to clients,
     * refreshes comparators and neighbors, and recomputes the emitted light level. Callers mark
     * the block changed; this reproduces the rest of the per-edit path as one pass, so a batch
     * that suppresses per-slot sync can finalize each block exactly once when it finishes.
     */
    private void finalizeAfterBatch() {
        if (level == null || level.isClientSide) {
            return;
        }
        syncToClients();
        level.updateNeighborsAt(getBlockPos(), getBlockState().getBlock());

        int newLight = ItemOps.calculateLightLevelFromItems(items);
        BlockState state = getBlockState();
        if (state.getValue(StorageStackBlock.LIGHT_LEVEL) != newLight) {
            level.setBlock(getBlockPos(), state.setValue(StorageStackBlock.LIGHT_LEVEL, newLight), Block.UPDATE_ALL);
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

    /** This block's own 27 slots, for callers that hold the block entity and need no pile-wide view. */
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
        itemsCap = LazyOptional.of(() -> new PileItemHandler(this));
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
                // Throttled. Don't drop the repack, or a gap opened faster than the cooldown would
                // sit unsorted until the next unthrottled edit. Defer it so the pile still settles
                // once the cooldown passes.
                scheduleDeferredRepack((int) (cooldownTicks - elapsed));
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
                sbe.finalizeAfterBatch();
            }
        }

        for (int i = pileStacks.size() - 1; i >= 0; i--) {
            StorageStackBE sbe = pileStacks.get(i);
            if (!sbe.isEmpty() || sbe.isPermanent() || sbe.hasStorageBlockAbove()) {
                break;
            }
            level.setBlock(sbe.getBlockPos(), Blocks.AIR.defaultBlockState(), 3);
        }
    }

    /**
     * Schedules a block tick that retries the repack once the cooldown has passed, unless one is
     * already pending for this block. {@link StorageStackBlock#tick} routes it back here, where the
     * cooldown will by then permit the sort.
     */
    private void scheduleDeferredRepack(int delayTicks) {
        if (level instanceof ServerLevel serverLevel) {
            Block block = getBlockState().getBlock();
            if (!serverLevel.getBlockTicks().hasScheduledTick(getBlockPos(), block)) {
                serverLevel.scheduleTick(getBlockPos(), block, Math.max(1, delayTicks));
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
                    ItemStack toInsert = from.copy();
                    toInsert.setCount(can);
                    ItemStack rem = handler.insertItem(i, toInsert, false);
                    int used = can - rem.getCount();
                    from.shrink(used);
                    moved += used;
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

    /**
     * Totals the input by exact item identity, then re-cuts each total into whole stacks plus at
     * most one remainder. Grouping by identity rather than by adjacency means two compatible
     * stacks always merge, wherever they sat in the pile. The result is returned in pile order.
     */
    private List<ItemStack> consolidate(List<ItemStack> stacks) {
        Map<StackKey, ItemStack> models = new LinkedHashMap<>();
        Map<StackKey, Integer> totals = new LinkedHashMap<>();

        for (ItemStack stack : stacks) {
            if (stack.isEmpty()) continue;

            StackKey key = new StackKey(stack.getItem(), stack.getDamageValue(), stack.getTag());
            models.putIfAbsent(key, stack);
            totals.merge(key, stack.getCount(), Integer::sum);
        }

        List<ItemStack> out = new ArrayList<>();

        for (Map.Entry<StackKey, Integer> entry : totals.entrySet()) {
            ItemStack model = models.get(entry.getKey());
            int max = Math.max(1, model.getMaxStackSize());
            int remaining = entry.getValue();

            while (remaining > 0) {
                ItemStack piece = model.copy();
                piece.setCount(Math.min(remaining, max));
                out.add(piece);
                remaining -= piece.getCount();
            }
        }

        out.sort(StackSort.COMPARATOR);
        return out;
    }

    /** Exact stack identity: two stacks merge if and only if their keys are equal. */
    private record StackKey(Item item, int damage, @Nullable CompoundTag tag) {
    }
}
