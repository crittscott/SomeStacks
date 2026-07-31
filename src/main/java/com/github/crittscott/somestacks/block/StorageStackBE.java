package com.github.crittscott.somestacks.block;

import com.github.crittscott.somestacks.ModRegistry;
import com.github.crittscott.somestacks.util.ItemOps;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemStackHandler;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * One Storage Stack: 27 item stacks drawn as a 3 x 3 x 3 grid.
 *
 * <p>The block owns its own slots, but it is not the unit of storage. A vertical run of these is a
 * {@link StoragePile}, and deposits, capability access, sorting and packing all act on the whole
 * run. Everything here that reaches past this block's own slots resolves the pile first.
 */
public class StorageStackBE extends BlockEntity {
    /** Slots in one block. The pile's flat slot range is this times its height. */
    public static final int SLOTS = 27;

    private boolean suppressSync = false;
    private boolean batchTouched = false;
    private boolean publishPending = false;

    private final ItemStackHandler items = new ItemStackHandler(SLOTS) {
        @Override
        protected void onContentsChanged(int slot) {
            setChanged();
            if (suppressSync) {
                batchTouched = true;
            } else {
                schedulePublish();
            }
        }

        @Override
        public boolean isItemValid(int slot, @Nonnull ItemStack stack) {
            return isValidStorageItem(stack);
        }
    };
    private LazyOptional<IItemHandler> itemsCap = LazyOptional.of(() -> new PileItemHandler(this));

    private int rotation = 0;
    private boolean permanent = false;

    /**
     * The comparator output last published for the pile this block is the base of, or -1 before the
     * first settle. Only the base's copy is consulted, and it is runtime state rather than saved
     * NBT: a freshly loaded pile has published nothing, so its first settle should notify.
     */
    private int publishedSignal = -1;

    /** The pile resolved for this block, good for the tick it was taken on. See {@link #pile()}. */
    private StoragePile cachedPile;
    private long cachedPileTick = Long.MIN_VALUE;

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
     * The pile this block belongs to, or null on the client and for a block being removed.
     *
     * <p>Resolved once a tick and held. A machine reading this block's item handler resolves the
     * pile once per slot it walks, which is hundreds of times a tick for one attached storage
     * network, and the run cannot change between two of those reads without passing through the
     * block's own place or remove hook. Those hooks drop the cache; the tick it was taken on is what
     * drops it for anything that edits the world without them.
     */
    @Nullable
    public StoragePile pile() {
        if (level == null || level.isClientSide) {
            return null;
        }

        long now = level.getGameTime();
        if (cachedPile != null && cachedPileTick == now) {
            return cachedPile;
        }

        cachedPile = StoragePile.resolve(level, getBlockPos());
        cachedPileTick = now;
        return cachedPile;
    }

    /** Drops the held pile, so the next caller walks the world again. */
    void invalidatePile() {
        cachedPile = null;
    }

    public int getRotation() {
        return rotation;
    }

    public void setRotation(int rotation) {
        this.rotation = rotation % 4;
        setChanged();
        syncToClients();
    }

    /** Whether this block survives being emptied. The pile's base block holds the pile's answer. */
    public boolean isPermanent() {
        return permanent;
    }

    /** Takes the pile's mode, which {@link StoragePile#settle()} propagates from the base. */
    void inheritPermanent(boolean value) {
        if (permanent != value) {
            permanent = value;
            setChanged();
            syncToClients();
        }
    }

    /**
     * Records the comparator output the pile is about to publish.
     *
     * @return whether it differs from the last one, and so whether the pile needs to tell its
     *         neighbours to read again
     */
    boolean exchangePublishedSignal(int signal) {
        if (publishedSignal == signal) {
            return false;
        }
        publishedSignal = signal;
        return true;
    }

    /** Gives a block that has just joined a pile the pile's presentation and mode. */
    void adoptPileState(boolean permanent, int rotation) {
        this.permanent = permanent;
        this.rotation = rotation % 4;
        setChanged();
        syncToClients();
    }

    /**
     * Deposits into the pile this block belongs to, filling from its base upward and growing the
     * column if it must. Which block of the pile the items were offered to makes no difference.
     */
    public int deposit(ItemStack fromHand) {
        return deposit(fromHand, null);
    }

    /**
     * @param placer the player responsible for any block this deposit creates, or null for
     *               automation. See {@link StoragePile#deposit}.
     */
    public int deposit(ItemStack fromHand, @Nullable ServerPlayer placer) {
        StoragePile pile = pile();
        return pile == null ? 0 : pile.deposit(fromHand, placer);
    }

    /**
     * Takes from one of this block's own slots, the cell the player targeted, and schedules the
     * settle that packs the rest of the pile down over the gap.
     */
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

        StoragePile pile = pile();
        if (pile != null) {
            pile.markDirty();
        }

        return taken;
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
     * Opens a run of edits that should publish as one. Per-slot sync is held back and the block
     * remembers whether anything actually changed, so {@link #endBatch()} can settle only the
     * blocks a pile-wide pass really touched.
     */
    void beginBatch() {
        suppressSync = true;
        batchTouched = false;
    }

    void endBatch() {
        suppressSync = false;
        if (batchTouched) {
            batchTouched = false;
            schedulePublish();
        }
    }

    /**
     * Closes a batch opened by the settle that is about to publish this block anyway, so the pass
     * that is already running pays for what it wrote instead of scheduling another one behind it.
     */
    void endBatchWithinSettle() {
        suppressSync = false;
        if (batchTouched) {
            batchTouched = false;
            publishPending = true;
        }
    }

    /**
     * Writes a slot only when it does not already hold exactly that stack, so a pile-wide rewrite
     * leaves untouched blocks clean and unsynced.
     */
    void setSlotIfChanged(int slot, ItemStack desired) {
        ItemStack current = items.getStackInSlot(slot);
        if (current.isEmpty() && desired.isEmpty()) {
            return;
        }
        if (current.getCount() == desired.getCount() && ItemStack.isSameItemSameTags(current, desired)) {
            return;
        }
        items.setStackInSlot(slot, desired);
    }

    /**
     * Records that this block owes a publication, and asks the pile to schedule the settle that
     * pays it.
     *
     * <p>Publishing a content change costs a block entity update packet and a light recompute, and
     * the settle behind it can move a stack into another block before any of that is worth sending.
     * Deferring both to the settle is what keeps a machine making capability calls all tick from
     * paying them per call, and what stops a stack from being sent once where it landed and again
     * where it was packed to.
     */
    private void schedulePublish() {
        if (level == null || level.isClientSide) {
            return;
        }
        publishPending = true;

        StoragePile pile = pile();
        if (pile != null) {
            pile.markDirty();
        }
    }

    /**
     * Pays what a deferred edit owes this block: contents to clients and the emitted light level.
     * The comparator value belongs to the pile rather than to one of its blocks, so
     * {@link StoragePile#settle()} publishes that once for the whole run.
     */
    void publishIfPending() {
        if (level == null || level.isClientSide || !publishPending) {
            return;
        }
        publishPending = false;
        syncToClients();

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
        if (tag.contains("Permanent")) permanent = tag.getBoolean("Permanent");
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.put("Items", items.serializeNBT());
        tag.putInt("Rotation", rotation);
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
}
