package com.github.crittscott.somestacks.block;

import com.github.crittscott.somestacks.CommonRegistry;
import com.github.crittscott.somestacks.ServerConfig;
import com.github.crittscott.somestacks.server.WorldEdits;
import com.github.crittscott.somestacks.util.SinglesCubeIdx;
import com.github.crittscott.somestacks.util.StackPlacement;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.List;

import javax.annotation.Nullable;

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
public final class SinglesColumn extends StackRun<SinglesStackBE> {
    private SinglesColumn(ServerLevel level, List<SinglesStackBE> blocks) {
        super(level, blocks, SinglesStackBE.SLOTS, CommonRegistry.singlesStackBlock());
    }

    /**
     * Resolves the column containing {@code pos}, or {@code null} when that position holds no
     * Singles Stack. Client-side levels never resolve a column: its bounds come from the server
     * config.
     */
    @Nullable
    public static SinglesColumn at(Level level, BlockPos pos) {
        if (!(level instanceof ServerLevel)) {
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
    static SinglesColumn resolve(ServerLevel level, BlockPos pos) {
        return new SinglesColumn(level, resolveBlocks(level, pos, SinglesStackBE.class));
    }

    /**
     * Drops the cached run held by every block a change at {@code pos} could have altered. See
     * {@link StoragePile#invalidateAround} for the reasoning.
     */
    static void invalidateAround(Level level, BlockPos pos) {
        StackRun.invalidateAround(level, pos, SinglesStackBE.class);
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
        StackRun.publishAround(level, pos, SinglesColumn::at);
    }

    /** Schedules the publication pass for the column at {@code pos}, if one is there. */
    static void markDirtyAt(Level level, BlockPos pos) {
        StackRun.markDirtyAt(level, pos, SinglesColumn::at);
    }

    /** The configured ceiling on column height, shared with Storage piles and Bar columns. */

    /**
     * Whether a Singles Stack may be created at {@code pos}: the contiguous column it would form,
     * counting the runs both below and above it, must fit the configured maximum.
     */
    public static boolean columnHasRoomFor(Level level, BlockPos pos) {
        return StackRun.columnHasRoomFor(level, pos, CommonRegistry.singlesStackBlock());
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
     * @return how many items were, or would be, taken from {@code stack}
     */
    @Override
    public int insertAt(int flatSlot, ItemStack stack, boolean simulate) {
        if (stack.isEmpty() || !SinglesStackBE.isValidSinglesItem(stack)) {
            return 0;
        }
        if (flatSlot < 0 || flatSlot >= advertisedSlots() || !cellAccepts(flatSlot)) {
            return 0;
        }
        if (simulate) {
            return 1;
        }
        if (flatSlot >= totalSlots() && !grow(flatSlot % SinglesStackBE.SLOTS)) {
            return 0;
        }

        ItemStack one = stack.copy();
        one.setCount(1);
        return handlerOf(flatSlot).insertItem(
                flatSlot % SinglesStackBE.SLOTS, one, false).isEmpty() ? 1 : 0;
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
        BlockPos pos = blockIndex < blocks.size()
                ? blocks.get(blockIndex).getBlockPos()
                : topPos().above();
        return SinglesStackBE.supportSeamBeneath(level, pos);
    }

    /**
     * Whether growth is permitted and the space above the column could take a block: height,
     * enablement, build height, replaceability, and every protection check available without side
     * effects. Simulating and committing share this predicate, so a simulated insertion cannot
     * promise a block the insertion itself would refuse. Growth carries no player, so protection
     * is checked against the level's automation actor, which is never exempt from spawn protection.
     */
    private boolean canGrow(VoxelShape finalCollision) {
        if (blocks.size() >= ServerConfig.maxPileHeight() || !ServerConfig.enableSinglesStackBlock()) {
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
        VoxelShape finalCollision = SinglesCubeIdx.shapeFor(slot, 0);
        if (!canGrow(finalCollision)) {
            return false;
        }

        BlockPos above = topPos().above();
        ServerPlayer editor = WorldEdits.automationActor(level);
        BlockState newStack = StackPlacement.stateFor(
                CommonRegistry.singlesStackBlock(), level, above);
        if (!WorldEdits.placeChecked(
                editor, level, above, newStack, Direction.DOWN, finalCollision)) {
            return false;
        }
        SinglesStackBE grown = (SinglesStackBE) level.getBlockEntity(above);

        blocks.add(grown);
        return true;
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
    @Override
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
