package com.github.crittscott.somestacks.block;

import com.github.crittscott.somestacks.CommonRegistry;
import com.github.crittscott.somestacks.server.WorldEdits;
import com.github.crittscott.somestacks.util.BlockType;
import com.github.crittscott.somestacks.util.ItemOps;
import com.github.crittscott.somestacks.util.StackPlacement;
import com.github.crittscott.somestacks.util.StackSort;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.Shapes;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;

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
        if (level.isClientSide) {
            return null;
        }
        if (!(level.getBlockEntity(pos) instanceof StorageStackBE sbe)) {
            return null;
        }
        return sbe.pile();
    }

    /**
     * Resolves the contiguous same-type run containing {@code pos} from the world, without
     * consulting an entity cache. Callers obtain cached run access through {@link
     * StackBlockEntity#itemRun()}.
     */
    static StoragePile resolve(ServerLevel level, BlockPos pos) {
        StoragePile run = new StoragePile(level, resolveBlocks(level, pos, StorageStackBE.class));
        run.cacheOnBlocks();
        return run;
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
        invalidateAround(level, pos, StorageStackBE.class);
    }

    /** Marks the pile at {@code pos} for settling, if one is there. */
    public static void markDirtyAt(Level level, BlockPos pos) {
        markDirtyAt(level, pos, StoragePile::at);
    }

    /**
     * Marks the pile at {@code pos} for settling, if one is there, attributing any block the
     * settlement removes to {@code actor}, or to automation when null.
     */
    static void markDirtyAt(Level level, BlockPos pos, @Nullable ServerPlayer actor) {
        StoragePile pile = at(level, pos);
        if (pile != null) {
            pile.markDirtyBy(actor);
        }
    }

    /**
     * Gives a Storage Stack that has just been placed the mode of the pile it joins, taking it from
     * the block below when there is one and otherwise from the block above. Placing a block beneath
     * a permanent pile makes it the new base, so without this the pile would silently become
     * temporary.
     */
    public static void inheritNeighbourPermanence(
            Level level, BlockPos pos, StorageStackBE placed) {
        StorageStackBE neighbour = null;
        if (level.getBlockEntity(pos.below()) instanceof StorageStackBE below) {
            neighbour = below;
        } else if (level.getBlockEntity(pos.above()) instanceof StorageStackBE above) {
            neighbour = above;
        }
        if (neighbour != null) {
            placed.inheritPermanent(neighbour.isPermanent());
        }
    }

    /** Whether this pile's blocks survive being emptied. The base block's flag is authoritative. */
    public boolean isPermanent() {
        return blocks.get(0).isPermanent();
    }

    /** Changes permanence and attributes any resulting deferred cleanup to {@code actor}. */
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

    /** Extracts from one pile slot and schedules the settle that packs the rest down over it. */
    @Override
    public ItemStack extract(int flatSlot, int amount, boolean simulate) {
        if (flatSlot < 0 || flatSlot >= totalSlots() || amount < 1) {
            return ItemStack.EMPTY;
        }
        ItemStack extracted =
                handlerOf(flatSlot).extractItem(localSlot(flatSlot), amount, simulate);
        if (!simulate && !extracted.isEmpty()) {
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
     *     same protections.
     * @return how many items were taken from {@code fromHand}, which is shrunk by that amount
     */
    public int deposit(ItemStack fromHand, @Nullable ServerPlayer placer) {
        if (fromHand.isEmpty() || !BlockType.STORAGE_STACK.admits(fromHand, placer)) {
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
     * Inserts into only the named flat slot, leaving the caller's stack unchanged. A successful
     * insertion schedules settlement to pack and sort the pile on the next block tick.
     *
     * <p>A slot in advertised headroom can grow the pile only when height, enablement, space,
     * obstruction, and the loader's automation edit authority permit it.
     *
     * @param simulate when true, evaluates current capacity and growth checks without storing or
     *     growing
     * @return the accepted item count, or the count currently acceptable when simulating; zero on
     *     refusal
     */
    @Override
    public int insertAt(int flatSlot, ItemStack stack, boolean simulate) {
        if (stack.isEmpty() || !BlockType.STORAGE_STACK.admits(stack, null)) {
            return 0;
        }
        if (flatSlot < 0 || flatSlot >= advertisedSlots()) {
            return 0;
        }

        if (simulate) {
            return Math.min(stack.getCount(), roomAt(flatSlot, stack, null));
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
            ItemStack rejected = handlerOf(flatSlot).insertItem(localSlot(flatSlot), offer, false);
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

    @Override
    public InsertionTest insertionTest() {
        BooleanSupplier growth = oncePerSweep(() -> canGrow(null));
        return (slot, stack) -> {
            if (stack.isEmpty()
                    || !BlockType.STORAGE_STACK.admits(stack, null)
                    || slot < 0
                    || slot >= advertisedSlots()) return 0;
            return Math.min(stack.getCount(), roomAt(slot, stack, growth));
        };
    }

    /**
     * How much of {@code stack} the one slot at {@code flatSlot} has room for. A slot in the block
     * above the pile is empty, so it has room for a whole stack — but only where growth would
     * actually be permitted, which {@link #canGrow} evaluates without side effects, so a simulation
     * cannot promise a slot the commit would refuse.
     */
    private int roomAt(int flatSlot, ItemStack stack, @Nullable BooleanSupplier growth) {
        if (flatSlot >= totalSlots()) {
            return (growth == null ? canGrow(null) : growth.getAsBoolean())
                    ? stack.getMaxStackSize()
                    : 0;
        }

        ItemStack inSlot = getSlot(flatSlot);
        if (inSlot.isEmpty()) {
            return stack.getMaxStackSize();
        }
        if (!ItemStack.isSameItemSameTags(inSlot, stack)) {
            return 0;
        }
        return Math.max(0, inSlot.getMaxStackSize() - inSlot.getCount());
    }

    /** Compatible partials first, then empty slots, both walked from the base upward. */
    private int fillExisting(ItemStack from) {
        int moved = 0;

        for (int i = 0; i < totalSlots() && !from.isEmpty(); i++) {
            ItemStack slot = getSlot(i);
            if (slot.isEmpty() || !ItemStack.isSameItemSameTags(slot, from)) {
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
        ItemStack rejected = handlerOf(flatSlot).insertItem(localSlot(flatSlot), offer, false);
        int used = amount - rejected.getCount();
        from.shrink(used);
        return used;
    }

    /**
     * Whether the pile could grow a full block on top: {@link #canGrowSpace} for {@code placer}
     * plus obstruction of the whole block, so a simulated deposit cannot promise a block the
     * deposit itself would refuse.
     */
    private boolean canGrow(@Nullable ServerPlayer placer) {
        return canGrowSpace(placer)
                && WorldEdits.isUnobstructed(level, topPos().above(), Shapes.block());
    }

    /**
     * Adds one block on top. The placement hook inherits its mode; growth uses the base rotation.
     */
    private boolean grow(@Nullable ServerPlayer placer) {
        BlockPos above = topPos().above();
        BlockState newStack =
                StackPlacement.stateFor(
                        CommonRegistry.STORAGE_STACK_BLOCK.get(),
                        level,
                        above,
                        blocks.get(0)
                                .getBlockState()
                                .getValue(StorageStackBlock.HORIZONTAL_FACING));
        if (!placeOnTop(placer, newStack, Shapes.block())) {
            return false;
        }
        StorageStackBE grown = (StorageStackBE) level.getBlockEntity(above);

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
        List<ItemStack> contents = new ArrayList<>();
        for (int i = 0; i < totalSlots(); i++) {
            ItemStack stack = getSlot(i);
            if (!stack.isEmpty()) {
                contents.add(stack);
            }
        }

        // The pieces refer to the slot stacks read above. Writing a slot replaces its stack rather
        // than changing it, so every model stays intact until the last slot is written.
        List<ItemOps.Piece> packed = packedOrder(contents);
        boolean permanent = isPermanent();

        for (StorageStackBE sbe : blocks) {
            sbe.beginBatch();
        }
        try {
            int index = 0;
            for (StorageStackBE sbe : blocks) {
                for (int slot = 0; slot < StorageStackBE.SLOTS; slot++) {
                    sbe.setSlotIfChanged(slot, index < packed.size() ? packed.get(index) : null);
                    index++;
                }
                sbe.inheritPermanent(permanent);
            }
        } finally {
            for (StorageStackBE sbe : blocks) {
                sbe.endBatchWithinSettle();
            }
        }

        trimEmptyTop(cleanupActor);
        for (StorageStackBE sbe : blocks) {
            sbe.publishIfPending(level);
        }
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
            boolean removed = WorldEdits.removeChecked(actor, level, blocks.get(i).getBlockPos());
            if (!removed) {
                break;
            }
            removedDownTo = i;
        }
        blocks.subList(removedDownTo, blocks.size()).clear();
    }

    /**
     * Consolidates the pile's contents by exact identity and sorts the pieces into the order they
     * are laid out from the base upward.
     */
    private static List<ItemOps.Piece> packedOrder(List<ItemStack> stacks) {
        List<SortedPiece> sorted = new ArrayList<>();
        ItemStack lastModel = null;
        StackSort.SortKey key = null;
        // A group's pieces are adjacent and share one model, so each group computes its key once.
        for (ItemOps.Piece piece : ItemOps.consolidate(stacks)) {
            if (piece.model() != lastModel) {
                lastModel = piece.model();
                key = StackSort.key(lastModel);
            }
            sorted.add(new SortedPiece(piece, key));
        }

        sorted.sort(
                (a, b) -> StackSort.comparePieces(a.key, a.piece.count(), b.key, b.piece.count()));
        List<ItemOps.Piece> out = new ArrayList<>(sorted.size());
        for (SortedPiece entry : sorted) {
            out.add(entry.piece);
        }
        return out;
    }

    private record SortedPiece(ItemOps.Piece piece, StackSort.SortKey key) {}
}
