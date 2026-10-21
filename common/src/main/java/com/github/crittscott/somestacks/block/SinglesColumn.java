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

import javax.annotation.Nullable;
import java.util.List;
import java.util.function.BooleanSupplier;

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
        super(level, blocks, SinglesStackBE.SLOTS, CommonRegistry.SINGLES_STACK_BLOCK.get());
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
     * Resolves the contiguous same-type run containing {@code pos} from the world, without consulting
     * an entity cache. Callers obtain cached run access through {@link StackBlockEntity#itemRun()}.
     */
    static SinglesColumn resolve(ServerLevel level, BlockPos pos) {
        SinglesColumn run = new SinglesColumn(level, resolveBlocks(level, pos, SinglesStackBE.class));
        run.cacheOnBlocks();
        return run;
    }

    /**
     * Drops the cached run held by every block a change at {@code pos} could have altered. See
     * {@link StoragePile#invalidateAround} for the reasoning.
     */
    static void invalidateAround(Level level, BlockPos pos) {
        invalidateAround(level, pos, SinglesStackBE.class);
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
        publishAround(level, pos, SinglesColumn::at);
    }

    /** Schedules the publication pass for the column at {@code pos}, if one is there. */
    static void markDirtyAt(@Nullable Level level, BlockPos pos) {
        if (level != null) {
            markDirtyAt(level, pos, SinglesColumn::at);
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
        return columnHasRoomFor(level, pos, CommonRegistry.SINGLES_STACK_BLOCK.get());
    }

    // Insertion

    /**
     * Inserts one item into only the named empty, supported cell,
     * leaving the caller's stack unchanged. Admission and support are checked before insertion.
     *
     * A slot in advertised headroom can grow the column only when height, enablement, space,
     * obstruction, and the loader's automation edit authority permit it.
     *
     * @param simulate when true, evaluates current capacity and growth checks without storing or growing
     * @return the accepted item count, or the count currently acceptable when simulating; zero on refusal
     */
    @Override
    public int insertAt(int flatSlot, ItemStack stack, boolean simulate) {
        if (stack.isEmpty() || !SinglesStackBE.isValidSinglesItem(stack)) {
            return 0;
        }
        if (flatSlot < 0 || flatSlot >= advertisedSlots() || !cellAccepts(flatSlot, null)) {
            return 0;
        }
        if (simulate) {
            return 1;
        }
        if (flatSlot >= totalSlots() && !grow(localSlot(flatSlot))) {
            return 0;
        }

        ItemStack one = stack.copy();
        one.setCount(1);
        return handlerOf(flatSlot).insertItem(localSlot(flatSlot), one, false).isEmpty() ? 1 : 0;
    }

    @Override
    public InsertionTest insertionTest() {
        BooleanSupplier growth = oncePerSweep(this::canGrowSpace);
        return (slot, stack) -> {
            if (stack.isEmpty() || !SinglesStackBE.isValidSinglesItem(stack)
                    || slot < 0 || slot >= advertisedSlots()) return 0;
            return cellAccepts(slot, growth) ? 1 : 0;
        };
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
    private boolean cellAccepts(int flatSlot, @Nullable BooleanSupplier growth) {
        int blockIndex = flatSlot / SinglesStackBE.SLOTS;
        int slot = localSlot(flatSlot);

        if (blockIndex < blocks.size()) {
            SinglesStackBE be = blocks.get(blockIndex);
            if (!be.getItems().getStackInSlot(slot).isEmpty()) {
                return false;
            }
            if (slot >= SinglesCubeIdx.LAYER_SIZE) {
                return !be.getItems().getStackInSlot(slot - SinglesCubeIdx.LAYER_SIZE).isEmpty();
            }
            return seamSupports(blockIndex, slot, be.getRotation());
        }

        return slot < SinglesCubeIdx.LAYER_SIZE && seamSupports(blockIndex, slot, 0)
                && (growth == null ? canGrowSpace() : growth.getAsBoolean())
                && WorldEdits.isUnobstructed(level, topPos().above(), SinglesCubeIdx.shapeFor(slot, 0));
    }

    /**
     * Reads only the supporting top cell, matching visual columns across differently rotated blocks.
     * The bottom block of the column stands on the world.
     */
    private boolean seamSupports(int blockIndex, int slot, int rotation) {
        if (blockIndex == 0) {
            return true;
        }
        SinglesStackBE below = blocks.get(blockIndex - 1);
        return SinglesCubeIdx.supportedBy(slot, rotation, below.getItems(), below.getRotation());
    }

    /**
     * Whether growth is permitted and the space above the column could take a block: height,
     * enablement, build height, replaceability, and every protection check available without side
     * effects. Simulating and committing share this predicate, so a simulated insertion cannot
     * promise a block the insertion itself would refuse. Growth carries no player, so protection
     * is checked against the level's automation actor, which is never exempt from spawn protection.
     */
    private boolean canGrow(VoxelShape finalCollision) {
        return canGrowSpace() && WorldEdits.isUnobstructed(level, topPos().above(), finalCollision);
    }

    /** Slot-independent automation eligibility, evaluated fresh outside an insertion sweep. */
    private boolean canGrowSpace() {
        if (blocks.size() >= maxHeight() || !ServerConfig.enableSinglesStackBlock()) {
            return false;
        }
        BlockPos above = topPos().above();
        if (level.isOutsideBuildHeight(above) || !level.getBlockState(above).canBeReplaced()) {
            return false;
        }
        return WorldEdits.mayPlace(level, above);
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
                CommonRegistry.SINGLES_STACK_BLOCK.get(), level, above);
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

        return blocks.get(flatSlot / SinglesStackBE.SLOTS).extractAt(localSlot(flatSlot));
    }
}
