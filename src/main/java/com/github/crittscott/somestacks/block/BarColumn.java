package com.github.crittscott.somestacks.block;

import com.github.crittscott.somestacks.ModRegistry;
import com.github.crittscott.somestacks.ServerConfig;
import com.github.crittscott.somestacks.server.Protection;
import com.github.crittscott.somestacks.util.BarCubeIdx;
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
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.items.IItemHandler;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

/**
 * A maximal contiguous vertical run of Bar Stacks, addressed as one inventory by automation.
 *
 * <p>Bars are a structure, not a bag: a slot index is a position, every bar must rest on something,
 * and nothing can be reordered without moving what the player sees. So both automated operations
 * address the position they are given, and both keep the structure standing without dropping
 * anything:
 *
 * <ul>
 *   <li>Insertion places one bar at the position named, and only where that position is empty and
 *       supported; where the position lies in the block above the column, it grows the column
 *       first. A caller walking the positions in order therefore fills the column layer by layer
 *       from the bottom, because each placement stands before the next position is offered.
 *   <li>Extraction takes the requested bar and moves the column's topmost bar into the hole. The
 *       topmost bar holds nothing up, and a slot vacated by extraction keeps the support it had,
 *       so the result always stands. When the hole is in a top layer the backfill restores that
 *       layer's occupancy exactly, leaving the seam under the block above untouched.
 * </ul>
 *
 * <p>Both preserve density: a column whose bars occupy a contiguous run of positions still does
 * afterwards. The player's own extraction is deliberately not this — it drops whatever the removed
 * bar was holding up.
 *
 * <p>An instance describes the run bounds at resolution time. Block entities cache it for the
 * current tick, and placement or removal invalidates every affected cache immediately.
 */
public final class BarColumn {
    private final Level level;
    private final List<BarStackBE> blocks;

    private BarColumn(Level level, List<BarStackBE> blocks) {
        this.level = level;
        this.blocks = blocks;
    }

    /**
     * Resolves the column containing {@code pos}, or {@code null} when that position holds no Bar
     * Stack. Client-side levels never resolve a column: its bounds come from the server config.
     */
    @Nullable
    public static BarColumn at(@Nullable Level level, BlockPos pos) {
        if (level == null || level.isClientSide) {
            return null;
        }
        if (!(level.getBlockEntity(pos) instanceof BarStackBE be)) {
            return null;
        }
        return be.column();
    }

    /**
     * Walks the world for the run containing {@code pos}. Every caller reaches this through the
     * cache {@link BarStackBE#column()} keeps; see there for why.
     */
    static BarColumn resolve(Level level, BlockPos pos) {
        BlockPos base = pos;
        while (level.getBlockEntity(base.below()) instanceof BarStackBE) {
            base = base.below();
        }

        List<BarStackBE> blocks = new ArrayList<>();
        BlockPos current = base;
        while (level.getBlockEntity(current) instanceof BarStackBE be) {
            blocks.add(be);
            current = current.above();
        }

        return new BarColumn(level, blocks);
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
     * reports; removing one splits a column into two runs whose values differ from the original
     * run. The upper run has a new bottom block that has published nothing, so it
     * notifies on its first look.
     *
     * <p>Call after {@link #invalidateAround}, and after any collapse the change starts, so the runs
     * are walked fresh and the value published is the one they settle on.
     */
    static void publishAround(Level level, BlockPos pos) {
        publishAt(level, pos);
        publishAt(level, pos.below());
        publishAt(level, pos.above());
    }

    private static void publishAt(Level level, BlockPos pos) {
        BarColumn column = at(level, pos);
        if (column != null) {
            column.publishComparatorSignal();
        }
    }

    /** Schedules the publication pass for the column at {@code pos}, if one is there. */
    static void markDirtyAt(@Nullable Level level, BlockPos pos) {
        BarColumn column = at(level, pos);
        if (column != null) {
            column.markDirty();
        }
    }

    private static void invalidateRun(Level level, BlockPos from, Direction direction) {
        BlockPos current = from;
        while (level.getBlockEntity(current) instanceof BarStackBE be) {
            be.invalidateColumn();
            current = current.relative(direction);
        }
    }

    /** The configured ceiling on column height, shared with Storage piles. */
    public static int maxHeight() {
        return ServerConfig.MAX_PILE_HEIGHT.get();
    }

    /**
     * Whether a Bar Stack may be created at {@code pos}: the contiguous column it would form,
     * counting the runs both below and above it, must fit the configured maximum.
     */
    public static boolean columnHasRoomFor(Level level, BlockPos pos) {
        return 1 + runLength(level, pos, Direction.DOWN) + runLength(level, pos, Direction.UP) <= maxHeight();
    }

    private static int runLength(Level level, BlockPos from, Direction direction) {
        Block bar = ModRegistry.BAR_STACK_BLOCK.get();
        int length = 0;
        BlockPos current = from.relative(direction);
        while (level.getBlockState(current).is(bar)) {
            length++;
            current = current.relative(direction);
        }
        return length;
    }

    public int height() {
        return blocks.size();
    }

    /** Positions the column actually holds: 64 per block, indexed from the bottom block upward. */
    public int totalSlots() {
        return blocks.size() * BarStackBE.SLOTS;
    }

    /**
     * Positions the column advertises to automation: its current positions plus one block of
     * reachable headroom while growth is allowed. Advertising the entire potential height would
     * expose unreachable gaps; advertising only current positions would leave automation no slot
     * through which to grow the column.
     */
    public int advertisedSlots() {
        int levels = blocks.size() < maxHeight() ? blocks.size() + 1 : blocks.size();
        return BarStackBE.SLOTS * levels;
    }

    public ItemStack getSlot(int flatSlot) {
        if (flatSlot < 0 || flatSlot >= totalSlots()) {
            return ItemStack.EMPTY;
        }
        return handlerOf(flatSlot).getStackInSlot(flatSlot % BarStackBE.SLOTS);
    }

    private BarStackBE blockOf(int flatSlot) {
        return blocks.get(flatSlot / BarStackBE.SLOTS);
    }

    private IItemHandler handlerOf(int flatSlot) {
        return blockOf(flatSlot).getItems();
    }

    // Comparator output

    /**
     * The share of the column's positions that hold a bar. A position takes one bar and no more, so
     * occupancy is the whole of it — which is what vanilla's container measure reduces to when a
     * slot's limit is one, rather than the sum of stack fractions a Storage pile computes.
     */
    public double fillLevel() {
        int total = totalSlots();
        if (total == 0) {
            return 0.0;
        }
        int occupied = 0;
        for (BarStackBE be : blocks) {
            IItemHandler handler = be.getItems();
            for (int slot = 0; slot < BarStackBE.SLOTS; slot++) {
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
     * position of every block occupied.
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
     * keeps that from costing a run-length of neighbor updates per bar moved, and reduces a
     * collapse crossing several signal values to the one update that outlives it. The bottom block
     * holds the last published value, because the bottom is what identifies a column.
     */
    void publishComparatorSignal() {
        if (blocks.isEmpty()) {
            return;
        }
        int signal = comparatorSignal();
        if (!blocks.get(0).exchangePublishedSignal(signal)) {
            return;
        }

        Block block = ModRegistry.BAR_STACK_BLOCK.get();
        for (BarStackBE be : blocks) {
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
     * <p>A position holds one bar, so a caller moving a stack through the capability makes one call
     * per bar. Deferring avoids an update packet, a full-column comparator walk, and a light
     * recompute for every individual call.
     */
    void markDirty() {
        if (blocks.isEmpty() || !(level instanceof ServerLevel serverLevel)) {
            return;
        }
        Block block = ModRegistry.BAR_STACK_BLOCK.get();
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
        for (BarStackBE be : blocks) {
            published |= be.publishIfPending();
        }
        if (published) {
            publishComparatorSignal();
        }
    }

    /** The highest occupied position in the column, or -1 when it holds no bars. */
    public int topmostOccupied() {
        for (int flatSlot = totalSlots() - 1; flatSlot >= 0; flatSlot--) {
            if (!getSlot(flatSlot).isEmpty()) {
                return flatSlot;
            }
        }
        return -1;
    }

    // Insertion

    /**
     * Places one bar at {@code flatSlot}, growing the column when that position lies in the block
     * above it.
     *
     * <p>Answering for the position it was given rather than for the column is what makes the
     * handler's slot range mean something: a caller that walks the range and sums what each position
     * accepts gets the column's real capacity; reporting whole-column capacity at every position
     * would multiply it.
     *
     * <p>A caller walking the range in ascending order still fills the column, because each
     * placement stands before the next position is offered: a filled layer supports the layer above
     * it by the time the walk arrives there.
     *
     * @param simulate when true, nothing is placed
     * @return whether a bar was, or would be, taken from {@code stack}
     */
    public boolean insertOneAt(int flatSlot, ItemStack stack, boolean simulate) {
        if (stack.isEmpty() || !BarStackBE.isValidBarItem(stack)) {
            return false;
        }
        if (flatSlot < 0 || flatSlot >= advertisedSlots() || !positionAccepts(flatSlot)) {
            return false;
        }
        if (simulate) {
            return true;
        }
        if (flatSlot >= totalSlots() && !grow(flatSlot % BarStackBE.SLOTS)) {
            return false;
        }

        ItemStack one = stack.copy();
        one.setCount(1);
        return handlerOf(flatSlot).insertItem(flatSlot % BarStackBE.SLOTS, one, false).isEmpty();
    }

    /**
     * Whether {@code flatSlot} could take a bar as the column stands: the position must be empty and
     * its footprint must overlap an occupied bar in the layer beneath it, which for a bottom layer
     * means the seam with the Bar Stack below, and for the bottom block of all means the world.
     *
     * <p>A position in the block above the column is checked against the block growth would put
     * there — empty and standing on the column's current top layer — and against the side-effect-free
     * checks in {@link #canGrow(VoxelShape)}. A simulation therefore cannot promise a position the
     * commit would refuse.
     */
    private boolean positionAccepts(int flatSlot) {
        int blockIndex = flatSlot / BarStackBE.SLOTS;
        int slot = flatSlot % BarStackBE.SLOTS;

        if (blockIndex < blocks.size()) {
            boolean[] occupancy = BarCubeIdx.occupancyOf(blocks.get(blockIndex).getItems());
            if (occupancy[slot]) {
                return false;
            }
            return BarCubeIdx.isGroundedIn(occupancy, slot, seamUnder(blockIndex));
        }

        VoxelShape finalCollision = BarCubeIdx.shapeFor(slot);
        return canGrow(finalCollision)
                && BarCubeIdx.isGroundedIn(new boolean[BarStackBE.SLOTS], slot, seamUnder(blockIndex));
    }

    /**
     * The support the block at {@code blockIndex} rests on: the top-layer occupancy of the block
     * below, or null for the bottom block of the column, which stands on the world and is grounded
     * outright.
     */
    @Nullable
    private boolean[] seamUnder(int blockIndex) {
        if (blockIndex == 0) {
            return null;
        }
        return BarCubeIdx.topLayerOccupancy(blocks.get(blockIndex - 1).getItems());
    }

    /**
     * Whether growth is permitted and the space above the column could take a block: height,
     * enablement, build height, replaceability, and every protection check available without side
     * effects. Simulating and committing share this predicate, so a simulated insertion cannot
     * promise a block the insertion itself would refuse. Growth carries no player, so protection
     * is checked against the level's fake player, which is never exempt from spawn protection.
     */
    private boolean canGrow(VoxelShape finalCollision) {
        if (blocks.size() >= maxHeight() || !ServerConfig.ENABLE_BAR_STACK_BLOCK.get()
                || !(level instanceof ServerLevel serverLevel)) {
            return false;
        }
        BlockPos above = topPos().above();
        if (level.isOutsideBuildHeight(above) || !level.getBlockState(above).canBeReplaced()) {
            return false;
        }
        return !Protection.isProtected(serverLevel, above)
                && Protection.isUnobstructed(serverLevel, above, finalCollision);
    }

    /** Adds one block on top, attributing the automated placement to the level's fake player. */
    private boolean grow(int slot) {
        VoxelShape finalCollision = BarCubeIdx.shapeFor(slot);
        if (!canGrow(finalCollision) || !(level instanceof ServerLevel serverLevel)) {
            return false;
        }

        BlockPos above = topPos().above();
        ServerPlayer editor = FakePlayerFactory.getMinecraft(serverLevel);
        BlockState newStack = StackPlacement.stateFor(
                ModRegistry.BAR_STACK_BLOCK.get(), serverLevel, above);
        if (!Protection.placeChecked(
                editor, serverLevel, above, newStack, Direction.DOWN, finalCollision)) {
            return false;
        }
        BarStackBE grown = (BarStackBE) level.getBlockEntity(above);

        blocks.add(grown);
        return true;
    }

    private BlockPos topPos() {
        return blocks.get(blocks.size() - 1).getBlockPos();
    }

    // Extraction

    /**
     * Takes the bar at {@code flatSlot} and fills the hole it leaves with the column's topmost bar.
     * Nothing is dropped and nothing is left unsupported.
     *
     * <p>The backfilled bar is written into the hole rather than inserted. Validity gates what a
     * deposit may add rather than what the column may carry, so a bar stored before an
     * {@code ss ingot} edit or a data pack reload narrowed the ingot set still moves.
     */
    public ItemStack extract(int flatSlot, int amount, boolean simulate) {
        if (flatSlot < 0 || flatSlot >= totalSlots() || amount < 1) {
            return ItemStack.EMPTY;
        }

        ItemStack inSlot = getSlot(flatSlot);
        if (inSlot.isEmpty()) {
            return ItemStack.EMPTY;
        }

        ItemStack taken = inSlot.copy();
        taken.setCount(1);
        if (simulate) {
            return taken;
        }

        for (BarStackBE be : blocks) {
            be.beginBatch();
        }
        try {
            handlerOf(flatSlot).extractItem(flatSlot % BarStackBE.SLOTS, 1, false);

            int top = topmostOccupied();
            if (top > flatSlot) {
                ItemStack moved = handlerOf(top).extractItem(top % BarStackBE.SLOTS, 1, false);
                blockOf(flatSlot).relocateInto(flatSlot % BarStackBE.SLOTS, moved);
            }
        } finally {
            for (BarStackBE be : blocks) {
                be.endBatch();
            }
        }

        trimEmptyTop();
        return taken;
    }

    /**
     * Removes the empty blocks at the top of the column. Nothing sits above them, so no seam and no
     * bar depends on their going.
     *
     * <p>Protection can refuse a removal, and the walk stops there rather than skipping past it:
     * the column keeps every block from the refused one down, so what it holds is what stands.
     */
    private void trimEmptyTop() {
        int keep = blocks.size();
        while (keep > 0 && blocks.get(keep - 1).isEmpty()) {
            keep--;
        }

        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }

        int removedDownTo = blocks.size();
        for (int i = blocks.size() - 1; i >= keep; i--) {
            if (!Protection.removeChecked(serverLevel, blocks.get(i).getBlockPos())) {
                break;
            }
            removedDownTo = i;
        }
        blocks.subList(removedDownTo, blocks.size()).clear();
    }
}
