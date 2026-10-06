package com.github.crittscott.somestacks.block;

import com.github.crittscott.somestacks.CommonRegistry;
import com.github.crittscott.somestacks.util.ItemOps;
import com.github.crittscott.somestacks.util.StorageCubeIdx;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;

import javax.annotation.Nullable;

/**
 * One Storage Stack: 27 item stacks drawn as a 3 x 3 x 3 grid.
 *
 * <p>The block owns its own slots, but it is not the unit of storage. A vertical run of these is a
 * {@link StoragePile}, and deposits, capability access, sorting and packing all act on the whole
 * run. Everything here that reaches past this block's own slots resolves the pile first.
 *
 * <p>Automation exposure is loader-specific and lives outside this class: Forge and NeoForge use
 * item-handler capabilities, while Fabric uses the Transfer API. {@link #getItems()} is the local
 * storage each loader-specific whole-pile view adapts.
 */
public class StorageStackBE extends StackBlockEntity {
    /** Slots in one block. The pile's flat slot range is this times its height. */
    public static final int SLOTS = StorageCubeIdx.CELLS;

    private static final String TAG_PERMANENT = "Permanent";

    private boolean permanent = false;
    @Nullable
    private ServerPlayer pendingCleanupActor;
    private boolean pendingCleanupUsesAutomation;

    public StorageStackBE(BlockPos pos, BlockState state) {
        super(CommonRegistry.STORAGE_STACK_BE.get(), pos, state, SLOTS);
    }

    /**
     * Whether Storage accepts this item. The broadest of the three rules: anything nonempty from a
     * namespace the server has not disabled. The per-item deny list applies to player deposits only.
     */
    public static boolean isValidStorageItem(ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        }
        if (ItemOps.isItemFromDisabledMod(stack)) {
            return false;
        }
        return true;
    }

    @Override
    protected boolean isStoredItemValid(ItemStack stack) {
        return isValidStorageItem(stack);
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
        return (StoragePile) itemRun();
    }

    @Override
    protected StackRunItemAccess resolveRun(ServerLevel serverLevel) {
        return StoragePile.resolve(serverLevel, getBlockPos());
    }

    public int getRotation() {
        return blockRotation();
    }

    /** Sets the outer block's rendered layout rotation modulo four. */
    public void setRotation(int rotation) {
        setBlockRotation(rotation);
    }

    /** Whether this block survives being emptied. The pile's base block is authoritative. */
    public boolean isPermanent() {
        return permanent;
    }

    /** Takes the pile's mode, which {@link StoragePile#settle()} propagates from the base. */
    void inheritPermanent(boolean value) {
        if (permanent != value) {
            permanent = value;
            setChanged();
        }
    }

    /** Gives a block that has just joined a pile the pile's presentation and mode. */
    void adoptPileState(boolean permanent, int rotation) {
        this.permanent = permanent;
        setChanged();
        setBlockRotation(rotation);
    }

    /**
     * Remembers who caused the next deferred settle. Multiple edits by one player retain that
     * player; automation, an unknown cause, or mixed actors conservatively use the automation
     * identity.
     */
    void noteCleanupActor(@Nullable ServerPlayer actor) {
        if (pendingCleanupUsesAutomation) {
            return;
        }
        if (actor == null) {
            pendingCleanupActor = null;
            pendingCleanupUsesAutomation = true;
        } else if (pendingCleanupActor == null) {
            pendingCleanupActor = actor;
        } else if (!pendingCleanupActor.getUUID().equals(actor.getUUID())) {
            pendingCleanupActor = null;
            pendingCleanupUsesAutomation = true;
        }
    }

    /** Takes and clears the actor attached to the next deferred settle. Null means automation. */
    @Nullable
    ServerPlayer takeCleanupActor() {
        ServerPlayer actor = pendingCleanupUsesAutomation ? null : pendingCleanupActor;
        pendingCleanupActor = null;
        pendingCleanupUsesAutomation = false;
        return actor;
    }

    /**
     * Deposits into the pile this block belongs to, filling from its base upward and growing the
     * column if it must. Which block of the pile the items were offered to makes no difference.
     *
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
        return extractAt(index, maxCount, playerHand, null);
    }

    /** Player-facing extraction variant that preserves the actor through deferred cleanup. */
    public ItemStack extractAt(int index, int maxCount, @Nullable ItemStack playerHand,
                               @Nullable ServerPlayer actor) {
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
            pile.markDirtyBy(actor);
        }

        return taken;
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
        if (current.getCount() == desired.getCount() && ItemStack.isSameItemSameComponents(current, desired)) {
            return;
        }
        items.setStackInSlot(slot, desired);
    }

    @Override
    protected void loadStackData(CompoundTag tag, HolderLookup.Provider registries) {
        if (tag.contains(TAG_PERMANENT)) permanent = tag.getBoolean(TAG_PERMANENT);
    }

    @Override
    protected void saveStackData(CompoundTag tag, HolderLookup.Provider registries) {
        tag.putBoolean(TAG_PERMANENT, permanent);
    }

    @Override
    protected void stripServerOnlyUpdateData(CompoundTag tag) {
        tag.remove(TAG_PERMANENT);
    }

}
