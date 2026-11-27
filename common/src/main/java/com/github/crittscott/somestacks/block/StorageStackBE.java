package com.github.crittscott.somestacks.block;

import com.github.crittscott.somestacks.CommonRegistry;
import com.github.crittscott.somestacks.util.BlockType;
import com.github.crittscott.somestacks.util.ItemOps;
import com.github.crittscott.somestacks.util.StorageCubeIdx;
import com.github.crittscott.somestacks.util.ViewRay;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

import javax.annotation.Nullable;

/**
 * One Storage Stack: 27 item stacks drawn as a 3 x 3 x 3 grid.
 *
 * <p>The block owns its own slots, but it is not the unit of storage. A vertical run of these is a
 * {@link StoragePile}, and deposits, capability access, sorting and packing all act on the whole
 * run. Everything here that reaches past this block's own slots resolves the pile first.
 *
 * <p>Automation exposure (a Forge {@code IItemHandler} capability or a Fabric Transfer API {@code
 * Storage}) is loader-specific and lives outside this class. Automation adapts {@link
 * StackBlockEntity#itemRun()} for whole-pile access; {@link #getItems()} exposes only this block's
 * local slots.
 */
public class StorageStackBE extends StackBlockEntity {
    /** Slots in one block. The pile's flat slot range is this times its height. */
    public static final int SLOTS = StorageCubeIdx.CELLS;

    private static final String TAG_PERMANENT = "Permanent";

    private boolean permanent = false;
    @Nullable private ServerPlayer pendingCleanupActor;
    private boolean pendingCleanupUsesAutomation;

    public StorageStackBE(BlockPos pos, BlockState state) {
        super(CommonRegistry.STORAGE_STACK_BE.get(), pos, state, SLOTS);
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
     *     automation. See {@link StoragePile#deposit}.
     */
    public int deposit(ItemStack fromHand, @Nullable ServerPlayer placer) {
        StoragePile pile = pile();
        return pile == null ? 0 : pile.deposit(fromHand, placer);
    }

    /** Fills the pile from its base wherever the ray was aimed; see {@link StoragePile#deposit}. */
    @Override
    public int depositFromHand(ViewRay ray, ItemStack fromHand, ServerPlayer player) {
        return deposit(fromHand, player);
    }

    /** A Storage pile takes a deposit wherever it is aimed, so it never yields to placement. */
    @Override
    public boolean acceptsDepositAlong(ViewRay ray) {
        return true;
    }

    /** Takes as many as the hand has room for, or the whole slot into an empty hand. */
    @Override
    public ItemStack extractIntoHand(int index, ItemStack hand, ServerPlayer player) {
        int room = hand.isEmpty() ? Integer.MAX_VALUE : hand.getMaxStackSize() - hand.getCount();
        return extractAt(index, room, hand, player);
    }

    /**
     * Takes up to {@code maxCount} from one of this block's own slots, the cell the player
     * targeted, when {@code playerHand} can hold it, and schedules the settle that packs the rest
     * of the pile down over the gap.
     */
    public ItemStack extractAt(
            int index, int maxCount, ItemStack playerHand, @Nullable ServerPlayer actor) {
        ItemStack inSlot = items.getStackInSlot(index);
        if (inSlot.isEmpty()) {
            return ItemStack.EMPTY;
        }

        if (!ItemOps.canTakeIntoHand(playerHand, inSlot)) {
            return ItemStack.EMPTY;
        }

        int toTake = Math.min(maxCount, inSlot.getCount());
        ItemStack taken = inSlot.copyWithCount(toTake);
        items.extractItem(index, toTake, false);

        StoragePile pile = pile();
        if (pile != null) {
            pile.markDirtyBy(actor);
        }

        return taken;
    }

    /**
     * Writes a slot only when it does not already hold exactly that piece, so a pile-wide rewrite
     * leaves untouched blocks clean and unsynced, and copies the piece only for a slot it writes.
     *
     * @param desired what the slot should hold, or null for empty
     */
    void setSlotIfChanged(int slot, @Nullable ItemOps.Piece desired) {
        ItemStack current = items.getStackInSlot(slot);
        if (desired == null) {
            if (!current.isEmpty()) {
                items.setStackInSlot(slot, ItemStack.EMPTY);
            }
            return;
        }
        if (current.getCount() == desired.count()
                && ItemStack.isSameItemSameTags(current, desired.model())) {
            return;
        }
        items.setStackInSlot(slot, desired.toStack());
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
    public boolean admits(ItemStack stack) {
        return BlockType.STORAGE_STACK.admits(stack, null);
    }

    @Override
    protected AABB cellBox(int index) {
        return StorageCubeIdx.GRID.localBox(index, getRotation());
    }

    @Override
    protected StackRun<?> resolveRun(ServerLevel serverLevel) {
        return StoragePile.resolve(serverLevel, getBlockPos());
    }
}
