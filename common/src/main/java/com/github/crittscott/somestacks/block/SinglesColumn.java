package com.github.crittscott.somestacks.block;

import com.github.crittscott.somestacks.CommonRegistry;
import com.github.crittscott.somestacks.ServerConfig;
import com.github.crittscott.somestacks.server.WorldEdits;
import com.github.crittscott.somestacks.util.SinglesCubeIdx;
import com.github.crittscott.somestacks.util.SlotAccess;
import com.github.crittscott.somestacks.util.StackPlacement;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.VoxelShape;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

/**
 * A maximal contiguous vertical run of Singles Stacks, addressed as one inventory by automation.
 *
 * <p>Cells are a structure, not a bag: a slot index is a position and every item must rest on the
 * cell beneath it or on the seam with the Singles Stack below. So insertion places one item at the
 * cell it is given, and only where that cell is empty and supported, growing the column when the
 * cell lies in the block above it. Refusing an unsupported cell rather than choosing another is what
 * keeps a slot index meaning one place, and no placement can leave an item hanging in the air.
 *
 * <p>Automation uses the same extraction behavior as the player: the target cell is emptied and
 * its visual column shifts down without drops or horizontal movement. Singles cannot use the Bar
 * column's topmost-item backfill because different items are not interchangeable.
 *
 * <p>An instance describes the run bounds at resolution time. Block entities cache it for the
 * current tick, and placement or removal invalidates every affected cache immediately.
 */
public final class SinglesColumn {
    private final Level level;
    private final List<SinglesStackBE> blocks;

    private SinglesColumn(Level level, List<SinglesStackBE> blocks) {
        this.level = level;
        this.blocks = blocks;
    }

    /**
     * Resolves the column containing {@code pos}, or {@code null} when that position holds no
     * Singles Stack. Client-side levels never resolve a column: its bounds come from the server
     * config.
     */
    @Nullable
    public static SinglesColumn at(@Nullable Level level, BlockPos pos) {
        if (level == null || level.isClientSide) {
            return null;
        }
        if (!(level.getBlockEntity(pos) instanceof SinglesStackBE be)) {
            return null;
        }
        return be.column();
    }

    /**
     * Walks the world for the run containing {@code pos}. Every caller reaches this through the
     * cache {@link SinglesStackBE#column()} keeps; see there for why.
     */
    static SinglesColumn resolve(Level level, BlockPos pos) {
        BlockPos base = pos;
        while (level.getBlockEntity(base.below()) instanceof SinglesStackBE) {
            base = base.below();
        }

        List<SinglesStackBE> blocks = new ArrayList<>();
        BlockPos current = base;
        while (level.getBlockEntity(current) instanceof SinglesStackBE be) {
            blocks.add(be);
            current = current.above();
        }

        return new SinglesColumn(level, blocks);
    }

    /**
     * Drops the cached run held by every block a change at {@code pos} could have altered. See
     * {@link StoragePile#invalidateAround} for the reasoning.
     */
    static void invalidateAround(Level level, BlockPos pos) {
        invalidateRun(level, pos, Direction.UP);
        invalidateRun(level, pos.above(), Direction.UP);
        invalidateRun(level, pos.below(), Direction.DOWN);
    }

    /**
     * Publishes the comparator output of every run a structural change at {@code pos} could have
     * altered. Adding a block lengthens a column, which changes the fill every one of its blocks
     * reports; removing one from the middle splits a column into two runs whose values differ from
     * the original run. The upper run has a new bottom block that has
     * published nothing, so it notifies on its first look.
     *
     * <p>Call after {@link #invalidateAround}, so the runs are walked fresh.
     */
    static void publishAround(Level level, BlockPos pos) {
        publishAt(level, pos);
        publishAt(level, pos.below());
        publishAt(level, pos.above());
    }

    private static void publishAt(Level level, BlockPos pos) {
        SinglesColumn column = at(level, pos);
        if (column != null) {
            column.publishComparatorSignal();
        }
    }

    /** Schedules the publication pass for the column at {@code pos}, if one is there. */
    static void markDirtyAt(@Nullable Level level, BlockPos pos) {
        SinglesColumn column = at(level, pos);
        if (column != null) {
            column.markDirty();
        }
    }

    private static void invalidateRun(Level level, BlockPos from, Direction direction) {
        BlockPos current = from;
        while (level.getBlockEntity(current) instanceof SinglesStackBE be) {
            be.invalidateColumn();
            current = current.relative(direction);
        }
    }

    /** The configured ceiling on column height, shared with Storage piles and Bar columns. */
    public static int maxHeight() {
        return ServerConfig.maxPileHeight();
    }

    /**
     * Whether a Singles Stack may be created at {@code pos}: the contiguous column it would form,
     * counting the runs both below and above it, must fit the configured maximum.
     */
    public static boolean columnHasRoomFor(Level level, BlockPos pos) {
        return 1 + runLength(level, pos, Direction.DOWN) + runLength(level, pos, Direction.UP) <= maxHeight();
    }

    private static int runLength(Level level, BlockPos from, Direction direction) {
        Block singles = CommonRegistry.SINGLES_STACK_BLOCK.get();
        int length = 0;
        BlockPos current = from.relative(direction);
        while (level.getBlockState(current).is(singles)) {
            length++;
            current = current.relative(direction);
        }
        return length;
    }

    /** Positions the column actually holds: 64 per block, indexed from the bottom block upward. */
    public int totalSlots() {
        return blocks.size() * SinglesStackBE.SLOTS;
    }

    /**
     * Positions the column advertises to automation: the ones it holds, plus one block's worth of
     * headroom while the configured height allows another block.
     *
     * <p>Neither extreme works. The full potential height leaves most of the range permanently
     * empty, and a caller polling its inventory re-derives that emptiness every tick. Only what the
     * column holds is worse: a caller offers items to the positions the range names and no others,
     * so a full column is never offered the insertion that grows it and automation cannot build
     * past the first block. One block of headroom is what one growth adds, and {@link #insertOneAt}
     * grows once for the cell it was given, so the range is reachable capacity and nothing more.
     */
    public int advertisedSlots() {
        int levels = blocks.size() < maxHeight() ? blocks.size() + 1 : blocks.size();
        return SinglesStackBE.SLOTS * levels;
    }

    public ItemStack getSlot(int flatSlot) {
        if (flatSlot < 0 || flatSlot >= totalSlots()) {
            return ItemStack.EMPTY;
        }
        return handlerOf(flatSlot).getStackInSlot(flatSlot % SinglesStackBE.SLOTS);
    }

    private SlotAccess handlerOf(int flatSlot) {
        return blocks.get(flatSlot / SinglesStackBE.SLOTS).getItems();
    }

    // Comparator output

    /**
     * The share of the column's cells that hold something. A cell takes one item and no more, so
     * occupancy is the whole of it — which is what vanilla's container measure reduces to when a
     * slot's limit is one, rather than the sum of stack fractions a Storage pile computes.
     */
    public double fillLevel() {
        int total = totalSlots();
        if (total == 0) {
            return 0.0;
        }
        int occupied = 0;
        for (SinglesStackBE be : blocks) {
            SlotAccess handler = be.getItems();
            for (int slot = 0; slot < SinglesStackBE.SLOTS; slot++) {
                if (!handler.getStackInSlot(slot).isEmpty()) {
                    occupied++;
                }
            }
        }
        return (double) occupied / total;
    }

    /**
     * The comparator output for the whole column, which is what every block of it reports. Vanilla's
     * container conversion, reserving the bottom of the range rather than scaling into it: any
     * nonempty column reads at least 1, so signal 0 means empty and nothing else. Full means every
     * cell of every block occupied.
     */
    public int comparatorSignal() {
        double fill = fillLevel();
        return fill > 0.0 ? Mth.floor(fill * 14.0) + 1 : 0;
    }

    /**
     * Tells the column's neighbors to read the comparator output again, but only when that output
     * has actually changed.
     *
     * <p>Every block reports the whole column's fill, so an edit anywhere in it changes the value
     * every block reports, including blocks that publish nothing of their own and are exactly
     * the ones a comparator may be sitting against. The whole run is notified; the guard is what
     * keeps that from costing a run-length of neighbor updates per item moved. The bottom block
     * holds the last published value, because the bottom is what identifies a column.
     */
    private void publishComparatorSignal() {
        if (blocks.isEmpty()) {
            return;
        }
        int signal = comparatorSignal();
        if (!blocks.get(0).exchangePublishedSignal(signal)) {
            return;
        }

        Block block = CommonRegistry.SINGLES_STACK_BLOCK.get();
        for (SinglesStackBE be : blocks) {
            // The comparator-aware update vanilla containers use: it reaches a comparator sitting one
            // block further away behind a solid block, which the plain neighbor update does not.
            level.updateNeighbourForOutputSignal(be.getBlockPos(), block);
        }
    }

    // Deferred publication

    /**
     * Schedules the publication pass for the next tick, on the column's bottom block so that every
     * edit anywhere in the run coalesces into one pass.
     *
     * <p>A cell holds one item, so a caller moving a stack through the capability makes one call
     * per item, and a single removal draws an item down out of every block above. Deferring is what
     * avoids an update packet, a full-column comparator walk, and a light recompute for every step.
     */
    void markDirty() {
        if (blocks.isEmpty() || !(level instanceof ServerLevel serverLevel)) {
            return;
        }
        Block block = CommonRegistry.SINGLES_STACK_BLOCK.get();
        BlockPos bottom = blocks.get(0).getBlockPos();
        if (!serverLevel.getBlockTicks().hasScheduledTick(bottom, block)) {
            serverLevel.scheduleTick(bottom, block, 1);
        }
    }

    /**
     * Publishes deferred contents and light for changed blocks, then publishes comparator output
     * once for the whole column.
     *
     * <p>Nothing outstanding means nothing to publish, and the comparator walk is skipped with it: a
     * structural change publishes through {@link #publishAround} at the moment it happens, so this
     * pass handles only changes deferred by content edits.
     */
    void publishPending() {
        boolean published = false;
        for (SinglesStackBE be : blocks) {
            published |= be.publishIfPending();
        }
        if (published) {
            publishComparatorSignal();
        }
    }

    // Insertion

    /**
     * Places one item at {@code flatSlot}, growing the column when that cell lies in the block above
     * it.
     *
     * <p>Answering for the cell it was given rather than for the column is what makes the handler's
     * slot range mean something: a caller that walks the range and sums what each cell accepts gets
     * the column's real capacity; reporting whole-column capacity at every cell would multiply it.
     *
     * <p>A caller walking the range in ascending order still fills the column, because each
     * placement stands before the next cell is offered: a filled layer supports the layer above it
     * by the time the walk arrives there.
     *
     * @param simulate when true, nothing is placed
     * @return whether an item was, or would be, taken from {@code stack}
     */
    public boolean insertOneAt(int flatSlot, ItemStack stack, boolean simulate) {
        if (stack.isEmpty() || !SinglesStackBE.isValidSinglesItem(stack)) {
            return false;
        }
        if (flatSlot < 0 || flatSlot >= advertisedSlots() || !cellAccepts(flatSlot)) {
            return false;
        }
        if (simulate) {
            return true;
        }
        if (flatSlot >= totalSlots() && !grow(flatSlot % SinglesStackBE.SLOTS)) {
            return false;
        }

        ItemStack one = stack.copy();
        one.setCount(1);
        return handlerOf(flatSlot).insertItem(flatSlot % SinglesStackBE.SLOTS, one, false).isEmpty();
    }

    /**
     * Whether {@code flatSlot} could take an item as the column stands: the cell must be empty and
     * the cell beneath it occupied, which for a bottom layer means the seam with the Singles Stack
     * below, and for the bottom block of all means the world.
     *
     * <p>A cell in the block above the column is checked against the block growth would put there —
     * empty, unrotated as a player's own placement is, standing on the column's current top layer —
     * and against the side-effect-free checks in {@link #canGrow(VoxelShape)}. A simulation therefore
     * cannot promise a cell the commit would refuse.
     */
    private boolean cellAccepts(int flatSlot) {
        int blockIndex = flatSlot / SinglesStackBE.SLOTS;
        int slot = flatSlot % SinglesStackBE.SLOTS;

        if (blockIndex < blocks.size()) {
            SinglesStackBE be = blocks.get(blockIndex);
            boolean[] occupancy = SinglesCubeIdx.occupancyOf(be.getItems());
            if (occupancy[slot]) {
                return false;
            }
            return SinglesCubeIdx.isGroundedIn(occupancy, slot, be.getRotation(), seamUnder(blockIndex));
        }

        VoxelShape finalCollision = SinglesCubeIdx.shapeFor(slot, 0);
        return canGrow(finalCollision) && SinglesCubeIdx.isGroundedIn(
                new boolean[SinglesStackBE.SLOTS], slot, 0, seamUnder(blockIndex));
    }

    /**
     * The support the block at {@code blockIndex} rests on: the top-layer occupancy of the block
     * below, read in visual columns because two stacked blocks may carry different rotations, or
     * null for the bottom block of the column, which stands on the world and is grounded outright.
     */
    @Nullable
    private boolean[] seamUnder(int blockIndex) {
        if (blockIndex == 0) {
            return null;
        }
        SinglesStackBE below = blocks.get(blockIndex - 1);
        return SinglesCubeIdx.topLayerOccupancy(below.getItems(), below.getRotation());
    }

    /**
     * Whether growth is permitted and the space above the column could take a block: height,
     * enablement, build height, replaceability, and every protection check available without side
     * effects. Simulating and committing share this predicate, so a simulated insertion cannot
     * promise a block the insertion itself would refuse. Growth carries no player, so protection
     * is checked against the level's automation actor, which is never exempt from spawn protection.
     */
    private boolean canGrow(VoxelShape finalCollision) {
        if (blocks.size() >= maxHeight() || !ServerConfig.enableSinglesStackBlock()
                || !(level instanceof ServerLevel serverLevel)) {
            return false;
        }
        BlockPos above = topPos().above();
        if (level.isOutsideBuildHeight(above) || !level.getBlockState(above).canBeReplaced()) {
            return false;
        }
        return !WorldEdits.isProtected(serverLevel, above)
                && WorldEdits.isUnobstructed(serverLevel, above, finalCollision);
    }

    /** Adds one block on top, attributing the automated placement to the level's automation actor. */
    private boolean grow(int slot) {
        VoxelShape finalCollision = SinglesCubeIdx.shapeFor(slot, 0);
        if (!canGrow(finalCollision) || !(level instanceof ServerLevel serverLevel)) {
            return false;
        }

        BlockPos above = topPos().above();
        ServerPlayer editor = WorldEdits.automationActor(serverLevel);
        BlockState newStack = StackPlacement.stateFor(
                CommonRegistry.SINGLES_STACK_BLOCK.get(), serverLevel, above);
        if (!WorldEdits.placeChecked(
                editor, serverLevel, above, newStack, Direction.DOWN, finalCollision)) {
            return false;
        }
        SinglesStackBE grown = (SinglesStackBE) level.getBlockEntity(above);

        blocks.add(grown);
        return true;
    }

    private BlockPos topPos() {
        return blocks.get(blocks.size() - 1).getBlockPos();
    }

    // Extraction

    /**
     * Takes the item at {@code flatSlot} through the block's own removal, so automation and the
     * player leave the column in the same state: the cells above the hole shift down one layer in
     * that column, drawing from the Singles Stacks above and removing any block the walk empties.
     *
     * @param flatSlot the position to extract, numbered from the bottom block upward
     * @param amount the requested maximum; any positive value can extract the position's one item
     * @param simulate whether to report the result without changing the column
     * @return the one item at {@code flatSlot}, or an empty stack when extraction is not possible
     */
    public ItemStack extract(int flatSlot, int amount, boolean simulate) {
        if (flatSlot < 0 || flatSlot >= totalSlots() || amount < 1) {
            return ItemStack.EMPTY;
        }

        ItemStack inSlot = getSlot(flatSlot);
        if (inSlot.isEmpty()) {
            return ItemStack.EMPTY;
        }

        if (simulate) {
            ItemStack taken = inSlot.copy();
            taken.setCount(1);
            return taken;
        }

        return blocks.get(flatSlot / SinglesStackBE.SLOTS).extractAt(flatSlot % SinglesStackBE.SLOTS);
    }
}
