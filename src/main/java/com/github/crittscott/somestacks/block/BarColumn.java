package com.github.crittscott.somestacks.block;

import com.github.crittscott.somestacks.ModRegistry;
import com.github.crittscott.somestacks.ServerConfig;
import com.github.crittscott.somestacks.server.Protection;
import com.github.crittscott.somestacks.util.BarCubeIdx;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.items.IItemHandler;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * A maximal contiguous vertical run of Bar Stacks, addressed as one inventory by automation.
 *
 * <p>Bars are a structure, not a bag: a slot index is a position, every bar must rest on something,
 * and nothing can be reordered without moving what the player sees. So the two automated operations
 * are the two that keep the structure standing without dropping anything:
 *
 * <ul>
 *   <li>Insertion takes the lowest empty position in the column that is already supported, growing
 *       the column when none is left. A column automation builds is therefore filled layer by
 *       layer from the bottom.
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
 * <p>Instances are resolved fresh per operation and are not held across world edits.
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
     * Positions the column advertises to automation: the ones it holds, plus one block's worth of
     * headroom while the configured height allows another block. See
     * {@link SinglesColumn#advertisedSlots()} for why it is neither the potential height nor the
     * real one.
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
     * Places bars at the lowest supported empty positions, growing the column while it must and
     * may.
     *
     * @param simulate when true, nothing is placed and {@code stack} is left alone
     * @return how many bars were taken from {@code stack}, which a real insertion shrinks
     */
    public int insert(ItemStack stack, boolean simulate) {
        if (stack.isEmpty() || !BarStackBE.isValidBarItem(stack)) {
            return 0;
        }

        int[] plan = planPlacements(stack.getCount());
        if (simulate || plan.length == 0) {
            return plan.length;
        }

        int placed = 0;
        for (BarStackBE be : blocks) {
            be.beginBatch();
        }
        try {
            for (int target : plan) {
                int blockIndex = target / BarStackBE.SLOTS;
                if (blockIndex >= blocks.size() && !grow()) {
                    break;
                }

                ItemStack one = stack.copy();
                one.setCount(1);
                if (!handlerOf(target).insertItem(target % BarStackBE.SLOTS, one, false).isEmpty()) {
                    break;
                }
                placed++;
            }
        } finally {
            for (BarStackBE be : blocks) {
                be.endBatch();
            }
        }

        stack.shrink(placed);
        return placed;
    }

    /**
     * Chooses the positions {@code wanted} bars would take, against a copy of the column's
     * occupancy so the same walk serves both a simulation and a real insertion.
     *
     * <p>The scan cursor only ever moves up. Grounding looks strictly downward, so filling a
     * position can never support one below it, and a position already passed over as unsupported
     * stays unsupported — one pass over the column therefore places any number of bars.
     *
     * <p>Only the position directly above the column can be weighed against the world, so the plan
     * grows once and stops. The block-place event a real growth fires cannot be consulted without
     * firing it, and a plan that assumed further growths would promise a capability caller more
     * than the insertion could deliver. One block holds a whole stack, so a single insertion never
     * needs a second.
     */
    private int[] planPlacements(int wanted) {
        int height = blocks.size();
        boolean[][] occupancy = new boolean[Math.max(maxHeight(), height)][];
        for (int i = 0; i < height; i++) {
            occupancy[i] = BarCubeIdx.occupancyOf(blocks.get(i).getItems());
        }

        // Only the one position above the column is known to be free, so the plan grows once. The
        // world tests behind canGrow() are deferred to the point of use, so a column with room to
        // spare never pays for them.
        boolean growthUnspent = true;
        int virtualHeight = height;

        int[] plan = new int[wanted];
        int placed = 0;
        int cursor = 0;

        while (placed < wanted) {
            int target = findSupportedEmpty(occupancy, virtualHeight, cursor);

            if (target < 0) {
                if (!growthUnspent || !canGrow()) {
                    break;
                }
                occupancy[virtualHeight] = new boolean[BarStackBE.SLOTS];
                virtualHeight++;
                growthUnspent = false;
                target = findSupportedEmpty(occupancy, virtualHeight, cursor);
                if (target < 0) {
                    // Nothing in the new block is supported: the top layer beneath it is empty.
                    break;
                }
            }

            occupancy[target / BarStackBE.SLOTS][target % BarStackBE.SLOTS] = true;
            plan[placed++] = target;
            cursor = target;
        }

        return Arrays.copyOf(plan, placed);
    }

    private static int findSupportedEmpty(boolean[][] occupancy, int height, int from) {
        int startBlock = from / BarStackBE.SLOTS;

        for (int b = startBlock; b < height; b++) {
            // The bottom block of a column stands on the world, which supports its bottom layer
            // outright; every other block rests on the seam below it.
            boolean[] seam = b == 0 ? null : BarCubeIdx.topLayerOf(occupancy[b - 1]);
            int startSlot = b == startBlock ? from % BarStackBE.SLOTS : 0;

            for (int slot = startSlot; slot < BarStackBE.SLOTS; slot++) {
                if (occupancy[b][slot]) {
                    continue;
                }
                if (BarCubeIdx.isGroundedIn(occupancy[b], slot, seam)) {
                    return b * BarStackBE.SLOTS + slot;
                }
            }
        }

        return -1;
    }

    /**
     * Whether growth is permitted and the space above the column could take a block: height,
     * enablement, build height, replaceability, and everything protection can answer without side
     * effects. Planning and committing share this predicate, so a simulated insertion cannot
     * promise a block the insertion itself would refuse. Growth carries no player, so protection
     * is weighed against the level's fake player, which is never exempt from spawn protection.
     */
    private boolean canGrow() {
        if (blocks.size() >= maxHeight() || !ServerConfig.ENABLE_BAR_STACK_BLOCK.get()
                || !(level instanceof ServerLevel serverLevel)) {
            return false;
        }
        BlockPos above = topPos().above();
        if (level.isOutsideBuildHeight(above) || !level.getBlockState(above).canBeReplaced()) {
            return false;
        }
        return !Protection.isProtected(serverLevel, above);
    }

    /** Adds one block on top. Automation carries no player, so growth answers to the fake player. */
    private boolean grow() {
        if (!canGrow() || !(level instanceof ServerLevel serverLevel)) {
            return false;
        }

        BlockPos above = topPos().above();
        ServerPlayer editor = FakePlayerFactory.getMinecraft(serverLevel);
        BlockState newStack = ModRegistry.BAR_STACK_BLOCK.get().defaultBlockState();
        if (!Protection.placeChecked(editor, serverLevel, above, newStack, Direction.DOWN)) {
            return false;
        }
        if (!(level.getBlockEntity(above) instanceof BarStackBE grown)) {
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
     * Takes the bar at {@code flatSlot} and fills the hole it leaves with the column's topmost bar.
     * Nothing is dropped and nothing is left unsupported.
     *
     * <p>The backfilled bar is written into the hole rather than inserted, because the column
     * already holds it and the hole is the position the extraction just vacated. Insertion would put
     * the Bar Stack's deposit rule in the way of a bar that is only changing position: contents
     * stored before an {@code ss ingot} edit or a data pack reload narrowed the ingot set stay
     * extractable, so they must stay movable too.
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
     */
    private void trimEmptyTop() {
        int keep = blocks.size();
        while (keep > 0 && blocks.get(keep - 1).isEmpty()) {
            keep--;
        }

        for (int i = blocks.size() - 1; i >= keep; i--) {
            level.setBlock(blocks.get(i).getBlockPos(), Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
        }
        blocks.subList(keep, blocks.size()).clear();
    }
}
