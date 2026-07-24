package com.github.crittscott.somestacks.block;

import com.github.crittscott.somestacks.ModRegistry;
import com.github.crittscott.somestacks.ServerConfig;
import com.github.crittscott.somestacks.server.Protection;
import com.github.crittscott.somestacks.util.SinglesCubeIdx;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.items.IItemHandler;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * A maximal contiguous vertical run of Singles Stacks, addressed as one inventory by automation.
 *
 * <p>Cells are a structure, not a bag: a slot index is a position and every item must rest on the
 * cell beneath it or on the seam with the Singles Stack below. So insertion ignores the requested
 * slot and takes the lowest supported empty cell in the column, growing it upward when none is
 * left, which is the only placement that cannot leave an item hanging in the air.
 *
 * <p>Extraction is the player's own removal, unchanged: the cell is emptied and the column shifts
 * down over it. That shift drops nothing and moves no item across the column horizontally, so
 * unlike a Bar column there is nothing for automation to do differently — and backfilling a hole
 * from the top, which is invisible among identical bars, would teleport an unrelated item down the
 * column here.
 *
 * <p>Instances are resolved fresh per operation and are not held across world edits.
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
        if (!(level.getBlockEntity(pos) instanceof SinglesStackBE)) {
            return null;
        }

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

    /** The configured ceiling on column height, shared with Storage piles and Bar columns. */
    public static int maxHeight() {
        return ServerConfig.MAX_PILE_HEIGHT.get();
    }

    /**
     * Whether a Singles Stack may be created at {@code pos}: the contiguous column it would form,
     * counting the runs both below and above it, must fit the configured maximum.
     */
    public static boolean columnHasRoomFor(Level level, BlockPos pos) {
        return 1 + runLength(level, pos, Direction.DOWN) + runLength(level, pos, Direction.UP) <= maxHeight();
    }

    private static int runLength(Level level, BlockPos from, Direction direction) {
        Block singles = ModRegistry.SINGLES_STACK_BLOCK.get();
        int length = 0;
        BlockPos current = from.relative(direction);
        while (level.getBlockState(current).is(singles)) {
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
        return blocks.size() * SinglesStackBE.SLOTS;
    }

    /**
     * Positions the column advertises to automation: always its full potential height, so the slot
     * count does not move as the column grows and shrinks. A column left over-tall by a lowered
     * configuration advertises its real height instead.
     */
    public int advertisedSlots() {
        return SinglesStackBE.SLOTS * Math.max(maxHeight(), blocks.size());
    }

    public ItemStack getSlot(int flatSlot) {
        if (flatSlot < 0 || flatSlot >= totalSlots()) {
            return ItemStack.EMPTY;
        }
        return handlerOf(flatSlot).getStackInSlot(flatSlot % SinglesStackBE.SLOTS);
    }

    private IItemHandler handlerOf(int flatSlot) {
        return blocks.get(flatSlot / SinglesStackBE.SLOTS).getItems();
    }

    // Insertion

    /**
     * Places items in the lowest supported empty cells, growing the column while it must and may.
     *
     * @param simulate when true, nothing is placed and {@code stack} is left alone
     * @return how many items were taken from {@code stack}, which a real insertion shrinks
     */
    public int insert(ItemStack stack, boolean simulate) {
        if (stack.isEmpty() || !SinglesStackBE.isValidSinglesItem(stack)) {
            return 0;
        }

        int[] plan = planPlacements(stack.getCount());
        if (simulate || plan.length == 0) {
            return plan.length;
        }

        int placed = 0;
        for (SinglesStackBE be : blocks) {
            be.beginBatch();
        }
        try {
            for (int target : plan) {
                int blockIndex = target / SinglesStackBE.SLOTS;
                if (blockIndex >= blocks.size() && !grow()) {
                    break;
                }

                ItemStack one = stack.copy();
                one.setCount(1);
                if (!handlerOf(target).insertItem(target % SinglesStackBE.SLOTS, one, false).isEmpty()) {
                    break;
                }
                placed++;
            }
        } finally {
            for (SinglesStackBE be : blocks) {
                be.endBatch();
            }
        }

        stack.shrink(placed);
        return placed;
    }

    /**
     * Chooses the cells {@code wanted} items would take, against a copy of the column's occupancy
     * so the same walk serves both a simulation and a real insertion.
     *
     * <p>The scan cursor only ever moves up. Grounding looks strictly downward, so filling a cell
     * can never support one below it, and a cell already passed over as unsupported stays
     * unsupported — one pass over the column therefore places any number of items.
     *
     * <p>Only the first growth can be weighed against the world; further ones are assumed to
     * succeed, since the block-place event a real growth fires cannot be consulted without firing
     * it.
     */
    private int[] planPlacements(int wanted) {
        int height = blocks.size();
        int capacity = Math.max(maxHeight(), height);
        boolean[][] occupancy = new boolean[capacity][];
        int[] rotations = new int[capacity];
        for (int i = 0; i < height; i++) {
            occupancy[i] = SinglesCubeIdx.occupancyOf(blocks.get(i).getItems());
            rotations[i] = blocks.get(i).getRotation();
        }

        boolean mayGrow = ServerConfig.ENABLE_SINGLES_STACK_BLOCK.get() && spaceAboveIsFree();
        int virtualHeight = height;

        int[] plan = new int[wanted];
        int placed = 0;
        int cursor = 0;

        while (placed < wanted) {
            int target = findSupportedEmpty(occupancy, rotations, virtualHeight, cursor);

            if (target < 0) {
                if (!mayGrow || virtualHeight >= maxHeight()) {
                    break;
                }
                occupancy[virtualHeight] = new boolean[SinglesStackBE.SLOTS];
                // A grown block is placed unrotated, as a player's own placement is.
                rotations[virtualHeight] = 0;
                virtualHeight++;
                target = findSupportedEmpty(occupancy, rotations, virtualHeight, cursor);
                if (target < 0) {
                    // Nothing in the new block is supported: the top layer beneath it is empty.
                    break;
                }
            }

            occupancy[target / SinglesStackBE.SLOTS][target % SinglesStackBE.SLOTS] = true;
            plan[placed++] = target;
            cursor = target;
        }

        return Arrays.copyOf(plan, placed);
    }

    private static int findSupportedEmpty(boolean[][] occupancy, int[] rotations, int height, int from) {
        int startBlock = from / SinglesStackBE.SLOTS;

        for (int b = startBlock; b < height; b++) {
            // The bottom block of a column stands on the world, which supports its bottom layer
            // outright; every other block rests on the seam below it. Two stacked blocks may carry
            // different rotations, so the seam is read in the visual columns both agree on.
            boolean[] seam = b == 0 ? null : SinglesCubeIdx.topLayerOf(occupancy[b - 1], rotations[b - 1]);
            int startSlot = b == startBlock ? from % SinglesStackBE.SLOTS : 0;

            for (int slot = startSlot; slot < SinglesStackBE.SLOTS; slot++) {
                if (occupancy[b][slot]) {
                    continue;
                }
                if (SinglesCubeIdx.isGroundedIn(occupancy[b], slot, rotations[b], seam)) {
                    return b * SinglesStackBE.SLOTS + slot;
                }
            }
        }

        return -1;
    }

    private boolean spaceAboveIsFree() {
        if (blocks.size() >= maxHeight()) {
            return false;
        }
        BlockPos above = topPos().above();
        return !level.isOutsideBuildHeight(above) && level.getBlockState(above).canBeReplaced();
    }

    /** Adds one block on top. Automation carries no player, so growth answers to the fake player. */
    private boolean grow() {
        if (!spaceAboveIsFree() || !ServerConfig.ENABLE_SINGLES_STACK_BLOCK.get()
                || !(level instanceof ServerLevel serverLevel)) {
            return false;
        }

        BlockPos above = topPos().above();
        ServerPlayer editor = FakePlayerFactory.getMinecraft(serverLevel);
        if (Protection.isProtected(editor, above)) {
            return false;
        }

        BlockState newStack = ModRegistry.SINGLES_STACK_BLOCK.get().defaultBlockState();
        if (!Protection.placeChecked(editor, serverLevel, above, newStack, Direction.DOWN)) {
            return false;
        }
        if (!(level.getBlockEntity(above) instanceof SinglesStackBE grown)) {
            return false;
        }

        grown.beginBatch();
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
