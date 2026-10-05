package com.github.crittscott.somestacks.block;

import com.github.crittscott.somestacks.CommonRegistry;
import com.github.crittscott.somestacks.ServerConfig;
import com.github.crittscott.somestacks.server.WorldEdits;
import com.github.crittscott.somestacks.util.BarCubeIdx;
import com.github.crittscott.somestacks.util.SlotAccess;
import com.github.crittscott.somestacks.util.StackPlacement;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.VoxelShape;

import javax.annotation.Nullable;
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
public final class BarColumn extends StackRun<BarStackBE> {
    private BarColumn(ServerLevel level, List<BarStackBE> blocks) {
        super(level, blocks, BarStackBE.SLOTS, CommonRegistry.BAR_STACK_BLOCK.get());
    }

    /**
     * Resolves the column containing {@code pos}, or {@code null} when that position holds no Bar
     * Stack. Client-side levels never resolve a column: its bounds come from the server config.
     */
    @Nullable
    public static BarColumn at(Level level, BlockPos pos) {
        if (!(level instanceof ServerLevel)) {
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
    static BarColumn resolve(ServerLevel level, BlockPos pos) {
        return new BarColumn(level, resolveBlocks(level, pos, BarStackBE.class));
    }

    /**
     * Drops the cached run held by every block a change at {@code pos} could have altered. See
     * {@link StoragePile#invalidateAround} for the reasoning.
     */
    static void invalidateAround(Level level, BlockPos pos) {
        StackRun.invalidateAround(level, pos, BarStackBE.class);
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
        StackRun.publishAround(level, pos, BarColumn::at);
    }

    /** Schedules the publication pass for the column at {@code pos}, if one is there. */
    static void markDirtyAt(Level level, BlockPos pos) {
        StackRun.markDirtyAt(level, pos, BarColumn::at);
    }

    /** The configured ceiling on column height, shared with Storage piles. */
    public static int maxHeight() {
        return StackRun.maxHeight();
    }

    /**
     * Whether a Bar Stack may be created at {@code pos}: the contiguous column it would form,
     * counting the runs both below and above it, must fit the configured maximum.
     */
    public static boolean columnHasRoomFor(Level level, BlockPos pos) {
        return StackRun.columnHasRoomFor(level, pos, CommonRegistry.BAR_STACK_BLOCK.get());
    }

    // Comparator output

    /**
     * The share of the column's positions that hold a bar. A position takes one bar and no more, so
     * occupancy is the whole of it — which is what vanilla's container measure reduces to when a
     * slot's limit is one, rather than the sum of stack fractions a Storage pile computes.
     */
    @Override
    public double fillLevel() {
        int total = totalSlots();
        if (total == 0) {
            return 0.0;
        }
        int occupied = 0;
        for (BarStackBE be : blocks) {
            SlotAccess handler = be.getItems();
            for (int slot = 0; slot < BarStackBE.SLOTS; slot++) {
                if (!handler.getStackInSlot(slot).isEmpty()) {
                    occupied++;
                }
            }
        }
        return (double) occupied / total;
    }

    /** The highest occupied position in the column, or -1 when it holds no bars. */
    private int topmostOccupied() {
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
     * @return how many bars were, or would be, taken from {@code stack}
     */
    @Override
    public int insertAt(int flatSlot, ItemStack stack, boolean simulate) {
        if (stack.isEmpty() || !BarStackBE.isValidBarItem(stack)) {
            return 0;
        }
        if (flatSlot < 0 || flatSlot >= advertisedSlots() || !positionAccepts(flatSlot)) {
            return 0;
        }
        if (simulate) {
            return 1;
        }
        if (flatSlot >= totalSlots() && !grow(flatSlot % BarStackBE.SLOTS)) {
            return 0;
        }

        ItemStack one = stack.copy();
        one.setCount(1);
        return handlerOf(flatSlot).insertItem(
                flatSlot % BarStackBE.SLOTS, one, false).isEmpty() ? 1 : 0;
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
        BlockPos pos = blockIndex < blocks.size()
                ? blocks.get(blockIndex).getBlockPos()
                : topPos().above();
        return BarStackBE.supportSeamBeneath(level, pos);
    }

    /**
     * Whether growth is permitted and the space above the column could take a block: height,
     * enablement, build height, replaceability, and every protection check available without side
     * effects. Simulating and committing share this predicate, so a simulated insertion cannot
     * promise a block the insertion itself would refuse. Growth carries no player, so protection
     * is checked against the level's automation actor, which is never exempt from spawn protection.
     */
    private boolean canGrow(VoxelShape finalCollision) {
        if (blocks.size() >= maxHeight() || !ServerConfig.enableBarStackBlock()) {
            return false;
        }
        BlockPos above = topPos().above();
        if (level.isOutsideBuildHeight(above) || !level.getBlockState(above).canBeReplaced()) {
            return false;
        }
        return !WorldEdits.isProtected(level, above)
                && WorldEdits.isUnobstructed(level, above, finalCollision);
    }

    /** Adds one block on top, attributing the automated placement to the level's automation actor. */
    private boolean grow(int slot) {
        VoxelShape finalCollision = BarCubeIdx.shapeFor(slot);
        if (!canGrow(finalCollision)) {
            return false;
        }

        BlockPos above = topPos().above();
        ServerPlayer editor = WorldEdits.automationActor(level);
        BlockState newStack = StackPlacement.stateFor(
                CommonRegistry.BAR_STACK_BLOCK.get(), level, above);
        if (!WorldEdits.placeChecked(
                editor, level, above, newStack, Direction.DOWN, finalCollision)) {
            return false;
        }
        BarStackBE grown = (BarStackBE) level.getBlockEntity(above);

        blocks.add(grown);
        return true;
    }

    // Extraction

    /**
     * Takes the bar at {@code flatSlot} and fills the hole it leaves with the column's topmost bar.
     * Nothing is dropped and nothing is left unsupported.
     *
     * <p>The backfilled bar is written into the hole rather than inserted. Validity gates what a
     * deposit may add rather than what the column may carry, so a bar stored before an
     * {@code ss ingot} edit or a data pack reload narrowed the ingot set still moves.
     *
     * @param flatSlot the position to extract, numbered from the bottom block upward
     * @param amount the requested maximum; any positive value can extract the position's one bar
     * @param simulate whether to report the result without changing the column
     * @return the one bar at {@code flatSlot}, or an empty stack when extraction is not possible
     */
    @Override
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

        int removedDownTo = blocks.size();
        for (int i = blocks.size() - 1; i >= keep; i--) {
            if (!WorldEdits.removeChecked(level, blocks.get(i).getBlockPos())) {
                break;
            }
            removedDownTo = i;
        }
        blocks.subList(removedDownTo, blocks.size()).clear();
    }
}
