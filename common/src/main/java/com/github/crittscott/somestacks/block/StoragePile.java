package com.github.crittscott.somestacks.block;

import com.github.crittscott.somestacks.server.WorldEdits;
import com.github.crittscott.somestacks.util.BlockType;
import com.github.crittscott.somestacks.util.StackSort;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.shapes.Shapes;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

import javax.annotation.Nullable;

/**
 * A maximal contiguous vertical run of Storage Stacks, treated as one inventory.
 *
 * <p>Every operation on a Storage Stack resolves the pile it belongs to and acts on the whole
 * column: deposits fill from the base upward whatever block or slot they were aimed at, the
 * capability exposes every block's slots as one flat range, and settling consolidates, sorts and
 * packs the entire height down. The base block's {@code permanent} flag is the pile's, and settling
 * propagates it, so a pile that is split or joined returns to a consistent state.
 *
 * <p>An instance describes the run bounds at resolution time. Block entities cache it for the
 * current tick, and placement or removal invalidates every affected cache immediately.
 */
public final class StoragePile extends StackRun<StorageStackBE> {
    private StoragePile(ServerLevel level, List<StorageStackBE> blocks) {
        super(level, blocks, StorageStackBE.SLOTS, BlockType.STORAGE_STACK);
    }

    /**
     * Resolves the pile containing {@code pos}, or {@code null} when that position holds no Storage
     * Stack. Client-side levels never resolve a pile: the pile is server state, and reading its
     * bounds would consult the server config.
     */
    @Nullable
    public static StoragePile at(Level level, BlockPos pos) {
        if (!(level instanceof ServerLevel)) {
            return null;
        }
        if (!(level.getBlockEntity(pos) instanceof StorageStackBE sbe)) {
            return null;
        }
        return sbe.pile();
    }

    /**
     * Walks the world for the run containing {@code pos}. Every caller reaches this through the
     * cache {@link StorageStackBE#pile()} keeps, because a capability read resolves the run once
     * per slot and a machine reading the whole handler does that hundreds of times a tick.
     */
    static StoragePile resolve(ServerLevel level, BlockPos pos) {
        return new StoragePile(level, resolveBlocks(level, pos, StorageStackBE.class));
    }

    /**
     * Drops the cached run held by every block a change at {@code pos} could have altered: the run
     * {@code pos} belongs to when it still holds a Storage Stack, and the runs above and below it
     * when it no longer does.
     *
     * <p>The cache expires on its own at the end of the tick, which covers anything that edits the
     * world without telling us. This is what covers the same tick, and the block's own place and
     * remove hooks are where every structural change passes, including the growth and trimming this
     * class does itself.
     */
    static void invalidateAround(Level level, BlockPos pos) {
        StackRun.invalidateAround(level, pos, StorageStackBE.class);
    }

    /** Marks the pile at {@code pos} for settling, if one is there. */
    static void markDirtyAt(Level level, BlockPos pos) {
        StackRun.markDirtyAt(level, pos, StoragePile::at);
    }

    /**
     * Marks the pile next to a removed block for settling, if one is there. A player who broke the
     * block is noted as responsible for the cleanup; a removal no player made (null) leaves the
     * pile's pending cleanup actor as it was.
     */
    static void markDirtyAfterRemoval(Level level, BlockPos pos, @Nullable ServerPlayer breaker) {
        StoragePile pile = at(level, pos);
        if (pile == null) {
            return;
        }
        if (breaker != null) {
            pile.markDirtyBy(breaker);
        } else {
            pile.markDirty();
        }
    }

    /**
     * Gives a Storage Stack that has just been placed the mode of the pile it joins, taking it from
     * the block below when there is one and otherwise from the block above. Placing a block beneath
     * a permanent pile makes it the new base, so without this the pile would silently become
     * temporary.
     */
    public static void adoptNeighbourState(Level level, BlockPos pos, StorageStackBE placed) {
        StorageStackBE neighbour = null;
        if (level.getBlockEntity(pos.below()) instanceof StorageStackBE below) {
            neighbour = below;
        } else if (level.getBlockEntity(pos.above()) instanceof StorageStackBE above) {
            neighbour = above;
        }
        if (neighbour != null) {
            placed.adoptPileState(neighbour.isPermanent(), neighbour.getRotation());
        }
    }

    /** Whether this pile's blocks survive being emptied. The base block's flag is authoritative. */
    public boolean isPermanent() {
        return blocks.get(0).isPermanent();
    }

    /**
     * Changes permanence and attributes any resulting deferred cleanup to {@code actor}; null means
     * automation.
     */
    public void setPermanent(boolean permanent, @Nullable ServerPlayer actor) {
        for (StorageStackBE sbe : blocks) {
            sbe.inheritPermanent(permanent);
        }
        markDirtyBy(actor);
    }

    /** Schedules settlement while preserving the actor responsible for possible block cleanup. */
    void markDirtyBy(@Nullable ServerPlayer actor) {
        if (!blocks.isEmpty()) {
            blocks.get(0).noteCleanupActor(actor);
        }
        markDirty();
    }

    /**
     * Extracts up to {@code amount} items from one pile slot and schedules the settle that packs
     * the rest down over it.
     *
     * @param flatSlot the pile slot to extract, numbered from the bottom block upward
     * @param amount the requested maximum
     * @return the extracted items, or an empty stack when the slot is invalid or empty
     */
    @Override
    public ItemStack extract(int flatSlot, int amount, boolean simulate) {
        if (amount < 1) {
            return ItemStack.EMPTY;
        }
        if (simulate) {
            ItemStack inSlot = getSlot(flatSlot);
            if (inSlot.isEmpty()) {
                return ItemStack.EMPTY;
            }
            return inSlot.copyWithCount(Math.min(amount, inSlot.getCount()));
        }
        if (flatSlot < 0 || flatSlot >= totalSlots()) {
            return ItemStack.EMPTY;
        }
        ItemStack extracted =
                handlerOf(flatSlot).extractItem(flatSlot % StorageStackBE.SLOTS, amount, false);
        if (!extracted.isEmpty()) {
            markDirtyBy(null);
        }
        return extracted;
    }

    // Deposit

    /**
     * Fills the pile from the base upward, ignoring where the deposit was aimed, and grows the
     * column while items remain and the configured height allows it.
     *
     * @param placer the player responsible for any block this deposit creates, or null for
     *     automation, whose growth is attributed to the automation actor and checked against the
     *     same protections
     * @return how many items were taken from {@code fromHand}, which is shrunk by that amount
     */
    public int deposit(ItemStack fromHand, @Nullable ServerPlayer placer) {
        if (!StorageStackBE.isValidStorageItem(fromHand)) {
            return 0;
        }

        int moved = 0;
        for (StorageStackBE sbe : blocks) {
            sbe.beginBatch();
        }
        try {
            moved = fillExisting(fromHand);
            while (!fromHand.isEmpty() && grow(placer)) {
                moved += fillExisting(fromHand);
            }
        } finally {
            for (StorageStackBE sbe : blocks) {
                sbe.endBatch();
            }
        }

        if (moved > 0) {
            markDirtyBy(placer);
        }
        return moved;
    }

    /**
     * Puts what it can of {@code stack} into the one slot at {@code flatSlot}, growing the pile
     * when that slot lies in the block above it.
     *
     * <p>A pile is genuinely a bag, so unlike the Singles and Bar columns its automated insertion
     * is an ordinary positional one: the slot named takes what a slot takes, and the settle scheduled
     * behind it packs the pile down from the base on the next tick. Filling from the base is
     * therefore still what a pile ends up doing, just a tick later than a player's own deposit does
     * it. Answering for the slot given rather than for the pile is what makes the handler's slot
     * range mean something: a caller that walks the range and sums what each slot accepts gets the
     * pile's real capacity; reporting whole-pile capacity at every slot would multiply it.
     *
     * <p>An automated insertion carries no player, so growth is checked against the loader-provided
     * automation actor that would place the block.
     *
     * @param simulate when true, nothing is stored and the pile does not grow
     * @return how many items were, or would be, taken from {@code stack}
     */
    @Override
    public int insertAt(int flatSlot, ItemStack stack, boolean simulate) {
        if (!StorageStackBE.isValidStorageItem(stack)) {
            return 0;
        }
        if (flatSlot < 0 || flatSlot >= advertisedSlots()) {
            return 0;
        }

        if (simulate) {
            return Math.min(stack.getCount(), roomAt(flatSlot, stack));
        }

        int moved;
        for (StorageStackBE sbe : blocks) {
            sbe.beginBatch();
        }
        try {
            // Growth hands back a block with a batch already open, which the close below pairs
            // with.
            if (flatSlot >= totalSlots() && !grow(null)) {
                return 0;
            }
            ItemStack offer = stack.copy();
            ItemStack rejected =
                    handlerOf(flatSlot).insertItem(flatSlot % StorageStackBE.SLOTS, offer, false);
            moved = stack.getCount() - rejected.getCount();
        } finally {
            for (StorageStackBE sbe : blocks) {
                sbe.endBatch();
            }
        }

        if (moved > 0) {
            markDirtyBy(null);
        }
        return moved;
    }

    /**
     * How much of {@code stack} the one slot at {@code flatSlot} has room for. A slot in the block
     * above the pile is empty, so it has room for a whole stack — but only where growth would
     * actually be permitted, which {@link #canGrow} evaluates without side effects, so a simulation
     * cannot promise a slot the commit would refuse.
     */
    private int roomAt(int flatSlot, ItemStack stack) {
        if (flatSlot >= totalSlots()) {
            return canGrow() ? stack.getMaxStackSize() : 0;
        }

        ItemStack inSlot = getSlot(flatSlot);
        if (inSlot.isEmpty()) {
            return stack.getMaxStackSize();
        }
        if (!ItemStack.isSameItemSameComponents(inSlot, stack)) {
            return 0;
        }
        return Math.max(0, inSlot.getMaxStackSize() - inSlot.getCount());
    }

    /** Compatible partials first, then empty slots, both walked from the base upward. */
    private int fillExisting(ItemStack from) {
        int moved = 0;

        for (int i = 0; i < totalSlots() && !from.isEmpty(); i++) {
            ItemStack slot = getSlot(i);
            if (slot.isEmpty() || !ItemStack.isSameItemSameComponents(slot, from)) {
                continue;
            }
            int room = slot.getMaxStackSize() - slot.getCount();
            if (room > 0) {
                moved += fillSlot(i, from, Math.min(from.getCount(), room));
            }
        }

        for (int i = 0; i < totalSlots() && !from.isEmpty(); i++) {
            if (getSlot(i).isEmpty()) {
                moved += fillSlot(i, from, Math.min(from.getCount(), from.getMaxStackSize()));
            }
        }

        return moved;
    }

    /**
     * Moves up to {@code amount} from {@code from} into one slot, shrinking {@code from} by what it
     * took.
     */
    private int fillSlot(int flatSlot, ItemStack from, int amount) {
        ItemStack offer = from.copyWithCount(amount);
        ItemStack rejected =
                handlerOf(flatSlot).insertItem(flatSlot % StorageStackBE.SLOTS, offer, false);
        int used = amount - rejected.getCount();
        from.shrink(used);
        return used;
    }

    /**
     * Whether automation could grow the pile a full block on top: {@link #canGrowSpace} for the
     * automation actor, and no entity in the way.
     */
    private boolean canGrow() {
        return canGrowSpace(null)
                && WorldEdits.isUnobstructed(level, topPos().above(), Shapes.block());
    }

    /**
     * Adds one block on top, opening a batch on it. The placement hook inherits the pile's mode and
     * rotation.
     */
    private boolean grow(@Nullable ServerPlayer placer) {
        if (!placeOnTop(placer, Shapes.block())) {
            return false;
        }
        StorageStackBE grown = (StorageStackBE) level.getBlockEntity(topPos().above());

        grown.beginBatch();
        blocks.add(grown);
        return true;
    }

    // Settling

    /**
     * Consolidates and sorts the whole pile, writes it back from the base upward, propagates the
     * base's permanent flag, removes empty blocks left at the top, and publishes all deferred
     * changes.
     *
     * <p>Slots that already hold what they should are left alone, so an edit that disturbs a few
     * stacks resyncs a few blocks rather than the whole column. Publishing after the trim means a
     * block that packing emptied away is never sent to clients only to be removed behind it.
     */
    public void settle() {
        ServerPlayer cleanupActor = blocks.get(0).takeCleanupActor();
        List<ItemStack> stored = new ArrayList<>(totalSlots());
        for (int slot = 0; slot < totalSlots(); slot++) {
            stored.add(getSlot(slot));
        }
        // One sort key per identity: the comparator meets each first-seen stack many times.
        Map<ItemStack, StackSort.SortKey> sortKeys = new IdentityHashMap<>();
        List<StackKey.Piece> packed =
                StackKey.consolidate(
                        stored,
                        Comparator.comparing(
                                stack -> sortKeys.computeIfAbsent(stack, StackSort::key)));
        boolean permanent = isPermanent();

        for (StorageStackBE sbe : blocks) {
            sbe.beginBatch();
        }
        try {
            int index = 0;
            for (StorageStackBE sbe : blocks) {
                for (int slot = 0; slot < StorageStackBE.SLOTS; slot++) {
                    StackKey.Piece desired = index < packed.size() ? packed.get(index) : null;
                    index++;
                    sbe.setSlotIfChanged(slot, desired);
                }
                sbe.inheritPermanent(permanent);
            }
        } finally {
            for (StorageStackBE sbe : blocks) {
                sbe.endBatchWithinSettle();
            }
        }

        trimEmptyTop(cleanupActor);
        publishPendingBlocks();
        publishComparatorSignal();
    }

    /**
     * Removes empty blocks from the top of the pile, stopping at the first block that still holds
     * something or is permanent. Packing has already pushed every item as far down as it goes, so
     * the empty blocks are exactly the run at the top: nothing below can be stranded by this.
     *
     * <p>Protection can refuse a removal, and the walk stops there rather than skipping past it:
     * the pile keeps every block from the refused one down, so what it holds is what stands.
     */
    private void trimEmptyTop(@Nullable ServerPlayer actor) {
        int keep = blocks.size();
        while (keep > 0) {
            StorageStackBE sbe = blocks.get(keep - 1);
            if (!sbe.isEmpty() || sbe.isPermanent()) {
                break;
            }
            keep--;
        }

        int removedDownTo = blocks.size();
        for (int i = blocks.size() - 1; i >= keep; i--) {
            if (!WorldEdits.removeChecked(actor, level, blocks.get(i).getBlockPos())) {
                break;
            }
            removedDownTo = i;
        }
        blocks.subList(removedDownTo, blocks.size()).clear();
    }
}
