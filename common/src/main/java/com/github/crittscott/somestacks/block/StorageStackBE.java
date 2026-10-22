package com.github.crittscott.somestacks.block;

import com.github.crittscott.somestacks.CommonRegistry;
import com.github.crittscott.somestacks.util.ItemOps;
import com.github.crittscott.somestacks.util.StorageCubeIdx;
import net.minecraft.core.BlockPos;
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
 * <p>Capability exposure ({@code ForgeCapabilities.ITEM_HANDLER} or its Fabric equivalent) is
 * loader-specific and lives outside this class. Automation adapts {@link StackBlockEntity#itemRun()}
 * for whole-pile access; {@link #getItems()} exposes only this block's local slots.
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
        super(CommonRegistry.STORAGE_STACK_BE.get(), pos, state, SLOTS, StorageStackBlock.LIGHT_LEVEL);
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

    /**
     * Returns the server pile containing this block, or null on the client or when removed.
     * Delegates to {@link StackBlockEntity#itemRun()}, whose per-tick cache is invalidated by
     * structural edits.
     */
    @Nullable
    public StoragePile pile() {
        return (StoragePile) itemRun();
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
        setRotation(rotation);
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
        if (current.getCount() == desired.getCount() && ItemStack.isSameItemSameTags(current, desired)) {
            return;
        }
        items.setStackInSlot(slot, desired);
    }

    @Override
    protected boolean isValidClientStackData(CompoundTag tag) {
        return !tag.contains(TAG_PERMANENT);
    }

    @Override
    protected void loadStackData(CompoundTag tag) {
        if (tag.contains(TAG_PERMANENT)) permanent = tag.getBoolean(TAG_PERMANENT);
    }

    @Override
    protected void saveStackData(CompoundTag tag) {
        tag.putBoolean(TAG_PERMANENT, permanent);
    }

    @Override
    protected void stripServerOnlyUpdateData(CompoundTag tag) {
        tag.remove(TAG_PERMANENT);
    }

    @Override
    protected boolean isStoredItemValid(ItemStack stack) {
        return isValidStorageItem(stack);
    }

    @Override
    protected StackRunItemAccess resolveRun(ServerLevel serverLevel) {
        return StoragePile.resolve(serverLevel, getBlockPos());
    }
}
