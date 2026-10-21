package com.github.crittscott.somestacks.block;

import com.github.crittscott.somestacks.CommonRegistry;
import com.github.crittscott.somestacks.ServerConfig;
import com.github.crittscott.somestacks.server.WorldEdits;
import com.github.crittscott.somestacks.util.StackSort;
import com.github.crittscott.somestacks.util.StackItemStorage;
import net.minecraft.advancements.CriteriaTriggers;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.shapes.Shapes;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.IntFunction;

import javax.annotation.Nullable;

/**
 * A maximal contiguous vertical run of Storage Stacks, treated as one inventory.
 *
 * <p>Every operation on a Storage Stack resolves the pile it belongs to and acts on the whole
 * column: deposits fill from the base upward whatever block or slot they were aimed at, the
 * automation view exposes every block's slots as one flat range, and settling consolidates, sorts and
 * packs the entire height down. The base block's {@code permanent} flag is the pile's, and
 * settling propagates it, so a pile that is split or joined returns to a consistent state.
 *
 * <p>An instance describes the run bounds at resolution time. Block entities cache it for the
 * current tick, and placement or removal invalidates every affected cache immediately.
 */
public final class StoragePile extends StackRun<StorageStackBE> {
    private StoragePile(ServerLevel level, List<StorageStackBE> blocks) {
        super(level, blocks, StorageStackBE.SLOTS, CommonRegistry.storageStackBlock());
    }

    /**
     * Resolves the pile containing {@code pos}, or {@code null} when that position holds no
     * Storage Stack. Client-side levels never resolve a pile: the pile is server state, and
     * reading its bounds would consult the server config.
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
     * cache {@link StorageStackBE#pile()} keeps, because an automation read resolves the run once per
     * slot and a machine reading the whole handler does that hundreds of times a tick.
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
        StackRun.invalidateAround(level, pos, CommonRegistry.storageStackBlock());
    }

    /** Marks the pile at {@code pos} for settling, if one is there. */
    static void markDirtyAt(Level level, BlockPos pos) {
        StackRun.markDirtyAt(level, pos, StoragePile::at);
    }

    /**
     * Whether a Storage Stack may be created at {@code pos}: the contiguous column it would form,
     * counting the runs both below and above it, must fit the configured maximum. Testing the
     * whole resulting column rather than one neighboring pile is what stops a block placed into
     * the gap between two piles from joining them into an over-tall one.
     */
    public static boolean columnHasRoomFor(Level level, BlockPos pos) {
        return StackRun.columnHasRoomFor(level, pos, CommonRegistry.storageStackBlock());
    }

    /**
     * Gives a Storage Stack that has just been placed the mode of the pile it joins, taking it
     * from the block below when there is one and otherwise from the block above. Placing a block
     * beneath a permanent pile makes it the new base, so without this the pile would silently
     * become temporary.
     */
    public static void adoptNeighbourState(Level level, BlockPos pos, StorageStackBE placed) {
        StorageStackBE neighbour = null;
        if (level.getBlockEntity(pos.below()) instanceof StorageStackBE below) {
            neighbour = below;
        } else if (level.getBlockEntity(pos.above()) instanceof StorageStackBE above) {
            neighbour = above;
        }
        if (neighbour != null) {
            placed.adoptPileState(neighbour.isPermanent());
        }
    }

    /** Whether this pile's blocks survive being emptied. The base block's flag is authoritative. */
    public boolean isPermanent() {
        return blocks.get(0).isPermanent();
    }

    /**
     * Changes permanence and attributes any resulting deferred cleanup to {@code actor}, or to
     * automation when it is null.
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
     * Extracts up to {@code amount} items from one pile slot and schedules the settle that packs the
     * rest down over it.
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
        ItemStack extracted = handlerOf(flatSlot).extractItem(flatSlot % StorageStackBE.SLOTS, amount);
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
     *               automation, whose growth is attributed to the automation actor and checked
     *               against the same protections
     * @return how many items were taken from {@code fromHand}, which is shrunk by that amount
     */
    public int deposit(ItemStack fromHand, @Nullable ServerPlayer placer) {
        if (fromHand.isEmpty() || !StorageStackBE.isValidStorageItem(fromHand)) {
            return 0;
        }

        int moved = 0;
        for (StorageStackBE sbe : blocks) {
            sbe.beginBatch();
        }
        try {
            moved = fillExisting(fromHand);
            while (!fromHand.isEmpty()) {
                // Captured before the deposit consumes the stack, as vanilla captures the placing item.
                ItemStack placedWith = fromHand.copy();
                if (!grow(placer)) {
                    break;
                }
                if (placer != null) {
                    CriteriaTriggers.PLACED_BLOCK.trigger(placer, topPos(), placedWith);
                }
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
     * Puts what it can of {@code stack} into the one slot at {@code flatSlot}, growing the pile when
     * that slot lies in the block above it.
     *
     * <p>Insertion is positional: the slot named takes what a slot takes, and the settle scheduled
     * behind it packs the pile down from the base on the next tick. Growth requires the slot to
     * lie in the block above the pile and is checked against the automation actor, since
     * automated insertion carries no player.
     *
     * @param simulate when true, nothing is stored and the pile does not grow
     * @return how many items were, or would be, taken from {@code stack}
     */
    @Override
    public int insertAt(int flatSlot, ItemStack stack, boolean simulate) {
        int accepted = acceptsAt(flatSlot, stack, this::getSlot);
        if (simulate || accepted == 0) {
            return accepted;
        }

        int moved;
        for (StorageStackBE sbe : blocks) {
            sbe.beginBatch();
        }
        try {
            // Growth hands back a block with a batch already open, which the close below pairs with.
            if (flatSlot >= totalSlots() && !grow(null)) {
                return 0;
            }
            ItemStack rejected = handlerOf(flatSlot).insertItem(
                    flatSlot % StorageStackBE.SLOTS, stack.copyWithCount(accepted));
            moved = accepted - rejected.getCount();
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
     * How much of {@code stack} the one slot at {@code flatSlot} has room for when it holds what
     * {@code contents} reports. A slot in the block above the pile has room only where growth would
     * actually be permitted, which {@link #canGrow} evaluates without side effects, so a simulation
     * cannot promise a slot the commit would refuse.
     */
    @Override
    public int acceptsAt(int flatSlot, ItemStack stack, IntFunction<ItemStack> contents) {
        if (stack.isEmpty() || !StorageStackBE.isValidStorageItem(stack)) {
            return 0;
        }
        if (flatSlot < 0 || flatSlot >= advertisedSlots()) {
            return 0;
        }
        if (flatSlot >= totalSlots() && !canGrow(null)) {
            return 0;
        }

        ItemStack inSlot = contents.apply(flatSlot);
        int room;
        if (inSlot.isEmpty()) {
            room = StackItemStorage.storageSlotLimit(stack);
        } else if (!ItemStack.isSameItemSameComponents(inSlot, stack)) {
            room = 0;
        } else {
            room = Math.max(0, StackItemStorage.storageSlotLimit(inSlot) - inSlot.getCount());
        }
        return Math.min(stack.getCount(), room);
    }

    /** Compatible partials first, then empty slots, both walked from the base upward. */
    private int fillExisting(ItemStack from) {
        int moved = 0;

        for (int i = 0; i < totalSlots() && !from.isEmpty(); i++) {
            ItemStack slot = getSlot(i);
            if (slot.isEmpty() || !ItemStack.isSameItemSameComponents(slot, from)) {
                continue;
            }
            int room = StackItemStorage.storageSlotLimit(slot) - slot.getCount();
            if (room > 0) {
                moved += fillSlot(i, from, Math.min(from.getCount(), room));
            }
        }

        for (int i = 0; i < totalSlots() && !from.isEmpty(); i++) {
            if (getSlot(i).isEmpty()) {
                moved += fillSlot(i, from, Math.min(from.getCount(), StackItemStorage.storageSlotLimit(from)));
            }
        }

        return moved;
    }

    /** Moves up to {@code amount} from {@code from} into one slot, shrinking {@code from} by what it took. */
    private int fillSlot(int flatSlot, ItemStack from, int amount) {
        ItemStack offer = from.copy();
        offer.setCount(amount);
        ItemStack rejected = handlerOf(flatSlot).insertItem(flatSlot % StorageStackBE.SLOTS, offer);
        int used = amount - rejected.getCount();
        from.shrink(used);
        return used;
    }

    /**
     * Whether growth is permitted and the space above the pile could take a block: height,
     * enablement, build height, replaceability, and every protection check available without side
     * effects. Planning and committing share this predicate so that a simulated deposit cannot
     * promise a block the deposit itself would refuse.
     *
     * <p>Spawn protection exempts operators, so the result depends on who is growing the pile: a
     * player's own deposit is checked against that player, and automation against the level's
     * automation actor, which is never exempt.
     */
    private boolean canGrow(@Nullable ServerPlayer placer) {
        return canGrowBlock(ServerConfig.enableStorageStackBlock(), placer)
                && WorldEdits.isUnobstructed(level, topPos().above(), Shapes.block());
    }

    /** Adds one block on top. The placement hook inherits its mode; growth uses the base rotation. */
    private boolean grow(@Nullable ServerPlayer placer) {
        if (!canGrow(placer)) {
            return false;
        }

        StorageStackBE grown = growBlock(placer, Shapes.block(), StorageStackBE.class);
        if (grown == null) return false;
        grown.beginBatch();
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
        Map<StackKey, SettlementGroup> groups = new LinkedHashMap<>();
        for (int slot = 0; slot < totalSlots(); slot++) {
            ItemStack stack = getSlot(slot);
            if (!stack.isEmpty()) {
                groups.computeIfAbsent(StackKey.of(stack), ignored -> new SettlementGroup(stack))
                        .count += stack.getCount();
            }
        }
        List<SettlementGroup> ordered = new ArrayList<>(groups.values());
        ordered.sort(Comparator.comparing(group -> group.sortKey));
        List<ItemStack> packed = new ArrayList<>();
        for (SettlementGroup group : ordered) {
            int remaining = group.count;
            int limit = StackItemStorage.storageSlotLimit(group.model);
            while (remaining > 0) {
                ItemStack piece = group.model.copy();
                piece.setCount(Math.min(remaining, limit));
                packed.add(piece);
                remaining -= piece.getCount();
            }
        }
        // Preserve live contents when their legal packed form needs additional slots.
        if (packed.size() > totalSlots()) {
            publishPendingBlocks();
            publishComparatorSignal();
            return;
        }
        boolean permanent = isPermanent();

        for (StorageStackBE sbe : blocks) {
            sbe.beginBatch();
        }
        try {
            int index = 0;
            for (StorageStackBE sbe : blocks) {
                for (int slot = 0; slot < StorageStackBE.SLOTS; slot++) {
                    ItemStack desired = index < packed.size() ? packed.get(index) : ItemStack.EMPTY;
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
        trimEmptyTop(actor, StorageStackBE::isPermanent);
    }

    /** One exact identity, its precomputed sort fields, and its total stored count. */
    private static final class SettlementGroup {
        private final ItemStack model;
        private final StackSort.SortKey sortKey;
        private int count;

        private SettlementGroup(ItemStack stack) {
            model = stack;
            sortKey = StackSort.key(stack);
        }
    }
}
