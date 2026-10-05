package com.github.crittscott.somestacks.gametest;

import com.github.crittscott.somestacks.block.SinglesStackBE;
import com.github.crittscott.somestacks.util.SinglesCubeIdx;
import com.github.crittscott.somestacks.util.ViewRay;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

import static com.github.crittscott.somestacks.gametest.GameTestScaffold.ORIGIN;
import static com.github.crittscott.somestacks.gametest.GameTestScaffold.check;
import static com.github.crittscott.somestacks.gametest.GameTestScaffold.checkEquals;
import static com.github.crittscott.somestacks.gametest.GameTestScaffold.droppedNear;
import static com.github.crittscott.somestacks.gametest.GameTestScaffold.firstBarItem;
import static com.github.crittscott.somestacks.gametest.GameTestScaffold.heldAt;
import static com.github.crittscott.somestacks.gametest.GameTestScaffold.placeSingles;
import static com.github.crittscott.somestacks.gametest.GameTestScaffold.seedSlot;
import static com.github.crittscott.somestacks.gametest.GameTestScaffold.signalAt;

/**
 * Singles support and gravity in a live level that does not touch loader-native automation
 * directly: grounding at the bottom of a column, a cell resting on the one below it, visual
 * columns lining up across differently rotated blocks, and extraction shifting a column down while
 * carrying each item's rotation with it. Capability/Transfer-API-facing tests stay in each loader's
 * own {@code SinglesColumnGameTests}.
 */
public final class SinglesColumnChecks {
    private SinglesColumnChecks() {}

    /**
     * The bottom layer stands on the world, while an unsupported higher cell rejects a deposit. To
     * reproduce in-game: aim at an empty bottom cell and deposit, then aim at a higher cell with
     * nothing beneath it. Only the bottom deposit succeeds.
     */
    public static void standaloneBottomIsGroundedAndFloatingCellIsRejected(GameTestHelper helper) {
        SinglesStackBE singles = placeSingles(helper, ORIGIN);
        check(SinglesStackBE.isValidSinglesItem(new ItemStack(Items.STICK)),
                "Stick is not valid for Singles tests");

        ItemStack grounded = new ItemStack(Items.STICK, 2);
        check(singles.depositAt(0, grounded), "Standalone bottom deposit failed");
        checkEquals(1, grounded.getCount(), "Grounded deposit remainder");

        ItemStack floating = new ItemStack(Items.STICK, 1);
        check(!singles.depositAt(16 + 1, floating), "Floating deposit succeeded");
        checkEquals(1, floating.getCount(), "Rejected deposit changed input");
        helper.succeed();
    }

    /**
     * A Singles item is supported by the cell directly beneath it. To reproduce in-game: deposit
     * into a bottom cell, then the same visual column one layer higher. Both items remain stacked.
     */
    public static void upperCellUsesTheCellImmediatelyBelow(GameTestHelper helper) {
        SinglesStackBE singles = placeSingles(helper, ORIGIN);
        int column = 5;
        int bottom = SinglesCubeIdx.indexFromColumn(column, 0);
        int secondLayer = SinglesCubeIdx.indexFromColumn(column, 1);

        check(singles.depositAt(bottom, new ItemStack(Items.STICK)),
                "Bottom deposit failed");
        check(singles.depositAt(secondLayer, new ItemStack(Items.PAPER)),
                "Supported upper deposit failed");
        helper.succeed();
    }

    /**
     * Support across a block seam follows visual columns despite different block rotations. To
     * reproduce in-game: rotate two stacked Singles blocks differently, fill a top cell of the
     * lower block, then deposit into the visually aligned bottom cell above. The deposit succeeds.
     */
    public static void differentlyRotatedBlocksShareVisualSeamColumns(GameTestHelper helper) {
        SinglesStackBE lower = placeSingles(helper, ORIGIN);
        SinglesStackBE upper = placeSingles(helper, ORIGIN.above());
        lower.setRotation(1);
        upper.setRotation(3);
        int visualColumn = 6;
        int lowerStorage =
                SinglesCubeIdx.storageColumnFromVisual(visualColumn, lower.getRotation());
        int upperStorage =
                SinglesCubeIdx.storageColumnFromVisual(visualColumn, upper.getRotation());
        int lowerTop = SinglesCubeIdx.indexFromColumn(lowerStorage, 3);
        int upperBottom = SinglesCubeIdx.indexFromColumn(upperStorage, 0);

        lower.getItems().insertItem(lowerTop, new ItemStack(Items.STICK), false);
        ItemStack offered = new ItemStack(Items.PAPER);

        check(upper.depositAt(upperBottom, offered),
                "Visual seam did not support the upper block");
        checkEquals(0, offered.getCount(), "Upper deposit remainder");
        helper.succeed();
    }

    /**
     * Extraction shifts only the aimed visual column down one layer and carries item rotations. To
     * reproduce in-game: make a three-item vertical Singles column, rotate the upper two items, and
     * extract the bottom item. The other two descend with their orientations unchanged.
     */
    public static void extractionShiftsOneColumnAndCarriesItemRotation(GameTestHelper helper) {
        SinglesStackBE singles = placeSingles(helper, ORIGIN);
        int column = 3;
        int bottom = SinglesCubeIdx.indexFromColumn(column, 0);
        int middle = SinglesCubeIdx.indexFromColumn(column, 1);
        int top = SinglesCubeIdx.indexFromColumn(column, 2);
        singles.getItems().insertItem(bottom, new ItemStack(Items.STICK), false);
        singles.getItems().insertItem(middle, new ItemStack(Items.PAPER), false);
        singles.getItems().insertItem(top, new ItemStack(Items.APPLE), false);
        singles.setCubeRotation(middle, 1);
        singles.setCubeRotation(top, 3);

        ItemStack extracted = singles.extractAt(bottom);

        checkEquals(Items.STICK, extracted.getItem(), "Extracted item");
        checkEquals(Items.PAPER, singles.getItems().getStackInSlot(bottom).getItem(),
                "Middle item did not shift to bottom");
        checkEquals(1, singles.getCubeRotation(bottom),
                "Middle item rotation did not move");
        checkEquals(Items.APPLE, singles.getItems().getStackInSlot(middle).getItem(),
                "Top item did not shift to middle");
        checkEquals(3, singles.getCubeRotation(middle),
                "Top item rotation did not move");
        check(singles.getItems().getStackInSlot(top).isEmpty(),
                "Top source cell remained occupied");
        checkEquals(0, singles.getCubeRotation(top),
                "Vacated cell retained a rotation");
        helper.succeed();
    }

    /**
     * Validity gates insertion only, so an item stored before the rule narrowed under it has to
     * survive the shift that closes the gap beneath it. An ingot stands in for such an item: a
     * Singles Stack refuses one from a deposit, because Bar accepts it, but may be holding one that
     * predates an {@code ss ingot} edit or a data pack reload.
     *
     * <p>To reproduce in-game: store an item in Singles, change the ingot list so Singles would now
     * reject it, place another item below it, and extract the lower item. The newly rejected stored
     * item moves down rather than being lost.
     */
    public static void shiftKeepsAnItemItWouldNoLongerAccept(GameTestHelper helper) {
        SinglesStackBE singles = placeSingles(helper, ORIGIN);
        BlockPos pos = singles.getBlockPos();
        Item legacy = firstBarItem();
        check(!SinglesStackBE.isValidSinglesItem(new ItemStack(legacy)),
                "Test needs an item a Singles Stack refuses");
        int bottom = SinglesCubeIdx.indexFromColumn(0, 0);
        int second = SinglesCubeIdx.indexFromColumn(0, 1);
        singles.getItems().insertItem(bottom, new ItemStack(Items.APPLE), false);
        seedSlot(singles.getItems(), second, new ItemStack(legacy));

        ItemStack extracted = singles.extractAt(bottom);

        checkEquals(Items.APPLE, extracted.getItem(), "Extracted item");
        int accounted = heldAt(helper, pos, legacy) + droppedNear(helper, pos, legacy);
        checkEquals(1, accounted, "Refused item after the shift that moved it");
        helper.succeed();
    }

    /**
     * The same conservation across a block boundary: the item handed down from the block above is
     * one the receiving block would refuse from a deposit.
     *
     * <p>To reproduce in-game: store an item in the bottom cell of an upper Singles block, change
     * the ingot list so Singles rejects it, and extract the aligned top item below. The stored item
     * crosses the seam rather than being lost.
     */
    public static void drawDownKeepsAnItemItWouldNoLongerAccept(GameTestHelper helper) {
        SinglesStackBE lower = placeSingles(helper, ORIGIN);
        SinglesStackBE upper = placeSingles(helper, ORIGIN.above());
        BlockPos lowerPos = lower.getBlockPos();
        BlockPos upperPos = upper.getBlockPos();
        Item legacy = firstBarItem();
        check(!SinglesStackBE.isValidSinglesItem(new ItemStack(legacy)),
                "Test needs an item a Singles Stack refuses");
        int bottom = SinglesCubeIdx.indexFromColumn(0, 0);
        lower.getItems().insertItem(bottom, new ItemStack(Items.APPLE), false);
        seedSlot(upper.getItems(), bottom, new ItemStack(legacy));

        ItemStack extracted = lower.extractAt(bottom);

        checkEquals(Items.APPLE, extracted.getItem(), "Extracted item");
        int accounted = heldAt(helper, lowerPos, legacy)
                + heldAt(helper, upperPos, legacy)
                + droppedNear(helper, lowerPos, legacy);
        checkEquals(1, accounted, "Refused item after being drawn down across the seam");
        helper.succeed();
    }

    /**
     * Singles gravity shifts occupied cells exactly one layer and does not compact a preexisting
     * gap. To reproduce in-game: use automation to leave a gap between two items in one column,
     * then extract the bottom item. The gap remains and the upper item descends only one layer.
     */
    public static void extractionPreservesAnExistingGap(GameTestHelper helper) {
        SinglesStackBE singles = placeSingles(helper, ORIGIN);
        int column = 2;
        int bottom = SinglesCubeIdx.indexFromColumn(column, 0);
        int second = SinglesCubeIdx.indexFromColumn(column, 1);
        int third = SinglesCubeIdx.indexFromColumn(column, 2);
        singles.getItems().insertItem(bottom, new ItemStack(Items.STICK), false);
        singles.getItems().insertItem(third, new ItemStack(Items.PAPER), false);

        singles.extractAt(bottom);

        check(singles.getItems().getStackInSlot(bottom).isEmpty(),
                "Existing gap was compacted away");
        checkEquals(Items.PAPER, singles.getItems().getStackInSlot(second).getItem(),
                "Upper item did not shift exactly one layer");
        helper.succeed();
    }

    /**
     * Extraction draws from the visually aligned column across a differently rotated block seam.
     * To reproduce in-game: rotate two stacked Singles blocks differently, put items across one
     * visual seam column, rotate the upper item, and extract the lower item. The upper item crosses
     * the seam with its item rotation intact and its emptied block disappears.
     */
    public static void extractionDrawsAcrossDifferentlyRotatedBlockBoundary(GameTestHelper helper) {
        SinglesStackBE lower = placeSingles(helper, ORIGIN);
        SinglesStackBE upper = placeSingles(helper, ORIGIN.above());
        lower.setRotation(1);
        upper.setRotation(2);
        int visualColumn = 9;
        int lowerColumn =
                SinglesCubeIdx.storageColumnFromVisual(visualColumn, lower.getRotation());
        int upperColumn =
                SinglesCubeIdx.storageColumnFromVisual(visualColumn, upper.getRotation());
        int lowerTop = SinglesCubeIdx.indexFromColumn(lowerColumn, 3);
        int upperBottom = SinglesCubeIdx.indexFromColumn(upperColumn, 0);
        lower.getItems().insertItem(lowerTop, new ItemStack(Items.STICK), false);
        upper.getItems().insertItem(upperBottom, new ItemStack(Items.PAPER), false);
        upper.setCubeRotation(upperBottom, 3);

        lower.extractAt(lowerTop);

        checkEquals(Items.PAPER, lower.getItems().getStackInSlot(lowerTop).getItem(),
                "Item did not cross the visual block seam");
        checkEquals(3, lower.getCubeRotation(lowerTop),
                "Cross-seam item rotation changed");
        check(helper.getLevel().getBlockEntity(upper.getBlockPos()) == null,
                "Emptied top block remained");
        helper.succeed();
    }

    /**
     * A deposit ray stops at the last empty cell before an occupied cell. To reproduce in-game:
     * place an item deeper in a Singles row and aim through the empty cells toward it. The new item
     * occupies the empty cell immediately in front of the stored item.
     */
    public static void depositTraceStopsAtLastEmptyBeforeOccupiedCell(GameTestHelper helper) {
        SinglesStackBE singles = placeSingles(helper, ORIGIN);
        singles.getItems().insertItem(8, new ItemStack(Items.STICK), false);
        BlockPos blockPos = singles.getBlockPos();

        int result = SinglesCubeIdx.traceAllPositions(
                traceThroughBlock(blockPos),
                blockPos,
                singles.getItems(),
                0);

        checkEquals(4, result, "Trace deposit index");
        helper.succeed();
    }

    /**
     * An unobstructed deposit ray selects its farthest supported cell. To reproduce in-game: aim
     * through an entirely empty supported Singles row and deposit. The item appears in the farthest
     * cell on that ray.
     */
    public static void depositTraceUsesFarthestCellWhenNothingIsOccupied(GameTestHelper helper) {
        SinglesStackBE singles = placeSingles(helper, ORIGIN);
        BlockPos blockPos = singles.getBlockPos();

        int result = SinglesCubeIdx.traceAllPositions(
                traceThroughBlock(blockPos),
                blockPos,
                singles.getItems(),
                0);

        checkEquals(12, result, "Trace deposit index");
        helper.succeed();
    }

    /**
     * Breaking the middle Singles block drops only its local items and does not pull the upper
     * block through the new gap. To reproduce in-game: put one distinct item in the same column of
     * each block in a three-block Singles run and break the middle block. Its item drops and the
     * upper item stays in the upper block.
     */
    public static void breakingMiddleBlockDropsItsContentsWithoutClosingTheGap(
            GameTestHelper helper) {
        SinglesStackBE lower = placeSingles(helper, ORIGIN);
        SinglesStackBE middle = placeSingles(helper, ORIGIN.above());
        SinglesStackBE upper = placeSingles(helper, ORIGIN.above(2));
        lower.getItems().insertItem(0, new ItemStack(Items.STONE), false);
        middle.getItems().insertItem(0, new ItemStack(Items.DIRT), false);
        upper.getItems().insertItem(0, new ItemStack(Items.APPLE), false);

        helper.setBlock(ORIGIN.above(), Blocks.AIR);

        checkEquals(1, GameTestScaffold.droppedNear(
                helper, middle.getBlockPos(), Items.DIRT), "Dropped middle contents");
        checkEquals(1, GameTestScaffold.heldAt(
                helper, lower.getBlockPos(), Items.STONE), "Lower contents");
        checkEquals(1, GameTestScaffold.heldAt(
                helper, upper.getBlockPos(), Items.APPLE), "Upper contents");
        check(helper.getLevel().getBlockEntity(upper.getBlockPos()) instanceof SinglesStackBE,
                "Upper block moved into the gap");
        checkEquals(Items.APPLE, upper.getItems().getStackInSlot(0).getItem(),
                "Upper item moved out of its block");
        helper.succeed();
    }

    /**
     * A ray entering the block's lowest north-west cell head on and running clear through it, so the
     * cells it meets are the column the trace is being asked about.
     */
    private static ViewRay traceThroughBlock(BlockPos blockPos) {
        double x = blockPos.getX() + 0.125;
        double y = blockPos.getY() + 0.125;
        return new ViewRay(new Vec3(x, y, blockPos.getZ() - 1.0), new Vec3(x, y, blockPos.getZ() + 2.0));
    }

    /**
     * The comparator range reserves 0 for an empty column, as a vanilla container's does, and a cell
     * holds one item, so full means every cell of every block occupied.
     *
     * <p>To reproduce in-game: compare an empty Singles column, the same column with one item, and a
     * full column. Their comparator outputs are 0, at least 1, and 15 respectively.
     */
    public static void comparatorReservesZeroForAnEmptyColumn(GameTestHelper helper) {
        SinglesStackBE singles = placeSingles(helper, ORIGIN);

        checkEquals(0, signalAt(helper, ORIGIN), "Empty column signal");

        singles.getItems().insertItem(0, new ItemStack(Items.STONE, 1), false);
        checkEquals(1, signalAt(helper, ORIGIN),
                "Signal for a single item in a whole column");

        for (int slot = 0; slot < SinglesStackBE.SLOTS; slot++) {
            singles.getItems().insertItem(slot, new ItemStack(Items.STONE, 1), false);
        }
        checkEquals(15, signalAt(helper, ORIGIN), "Full column signal");
        helper.succeed();
    }

    /**
     * Every block in a Singles column reports the same whole-column fill. To reproduce in-game:
     * make a two-block column with one block's cells full and the other empty, then place comparators
     * against both blocks. Both report the half-full value.
     */
    public static void comparatorReadsTheWholeColumnFromEveryBlock(GameTestHelper helper) {
        SinglesStackBE lower = placeSingles(helper, ORIGIN);
        placeSingles(helper, ORIGIN.above());
        for (int slot = 0; slot < SinglesStackBE.SLOTS; slot++) {
            lower.getItems().insertItem(slot, new ItemStack(Items.STONE, 1), false);
        }

        // Half the column's cells are occupied, and both blocks report that rather than their own.
        checkEquals(8, signalAt(helper, ORIGIN), "Lower block signal");
        checkEquals(8, signalAt(helper, ORIGIN.above()), "Upper block signal");
        helper.succeed();
    }

    /**
     * The fill is measured against the column's current height, so a run that loses a block reports
     * the same contents as a larger share of a smaller column.
     *
     * <p>To reproduce in-game: fill the lower half of a two-block Singles column, read its comparator,
     * then remove the empty upper block. The lower block's output rises from half full to full.
     */
    public static void comparatorFollowsAColumnLosingABlock(GameTestHelper helper) {
        SinglesStackBE lower = placeSingles(helper, ORIGIN);
        placeSingles(helper, ORIGIN.above());
        for (int slot = 0; slot < SinglesStackBE.SLOTS; slot++) {
            lower.getItems().insertItem(slot, new ItemStack(Items.STONE, 1), false);
        }
        checkEquals(8, signalAt(helper, ORIGIN), "Signal before the block was lost");

        helper.setBlock(ORIGIN.above(), Blocks.AIR);

        checkEquals(15, signalAt(helper, ORIGIN), "Signal after the block was lost");
        helper.succeed();
    }
}
