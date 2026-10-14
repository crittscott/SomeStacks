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
import net.minecraft.world.level.block.Block;
import com.github.crittscott.somestacks.block.SinglesColumn;
import com.github.crittscott.somestacks.block.SinglesStackBlock;
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
     * To reproduce: build three occupied Singles blocks, rotate the upper block and some of its
     * items, and put comparators beside the ends. Break the middle block. Only its local contents
     * drop; upper items keep their positions and rotations, and both surviving runs update fill
     * and light. The fixture drains placement ticks, then inserts an upper glowstone in the same
     * tick as removal to test pending publication at the new upper base without invoking gravity.
     */
    public static void middleRemovalPreservesAndPublishesBothSurvivingColumns(GameTestHelper helper) {
        SinglesStackBE lower = placeSingles(helper, ORIGIN);
        SinglesStackBE middle = placeSingles(helper, ORIGIN.above());
        SinglesStackBE upper = placeSingles(helper, ORIGIN.above(2));
        lower.getItems().setStackInSlot(0, new ItemStack(Items.GLOWSTONE));
        middle.getItems().setStackInSlot(0, new ItemStack(Items.DIAMOND));
        upper.setRotation(2);
        for (int slot = 0; slot < 4; slot++) {
            upper.getItems().setStackInSlot(slot, new ItemStack(Items.PAPER));
            upper.setCubeRotation(slot, 3 - slot);
        }
        BlockPos lowerComparator = GameTestScaffold.placeComparator(helper, ORIGIN);
        BlockPos upperComparator = GameTestScaffold.placeComparator(helper, ORIGIN.above(2));
        helper.runAfterDelay(6, () -> {
            check(!helper.getLevel().getBlockTicks().hasScheduledTick(upper.getBlockPos(),
                    upper.getBlockState().getBlock()), "Fixture retained an upper placement tick");
            SinglesColumn cached = lower.column();
            check(cached != null && cached == upper.column(), "Ends did not share the initial cache");
            checkEquals(3, cached.height(), "Initial height");
            upper.getItems().setStackInSlot(4, new ItemStack(Items.GLOWSTONE));
            upper.setCubeRotation(4, 1);
            checkEquals(0, upper.getBlockState().getValue(SinglesStackBlock.LIGHT_LEVEL),
                    "Upper edit published before the split");
            check(helper.getLevel().setBlock(middle.getBlockPos(), Blocks.AIR.defaultBlockState(),
                    Block.UPDATE_ALL), "Middle removal failed");
            checkEquals(1, droppedNear(helper, middle.getBlockPos(), Items.DIAMOND), "Middle drop count");
            checkEquals(0L, GameTestScaffold.droppedInColumn(helper, lower.getBlockPos(), upper.getBlockPos())
                    .stream().filter(entity -> !entity.getItem().is(Items.DIAMOND)).count(),
                    "A surviving block dropped contents");
            helper.runAfterDelay(6, () -> {
                SinglesColumn below = lower.column();
                SinglesColumn above = upper.column();
                check(below != null && above != null && below != above, "Split columns not independent");
                checkEquals(lower.getBlockPos(), below.basePos(), "Lower base");
                checkEquals(upper.getBlockPos(), above.basePos(), "Upper base");
                checkEquals(1, below.height(), "Lower height");
                checkEquals(1, above.height(), "Upper height");
                checkEquals(1, GameTestScaffold.occupied(lower.getItems()), "Lower contents");
                checkEquals(5, GameTestScaffold.occupied(upper.getItems()), "Upper contents");
                checkEquals(2, upper.getRotation(), "Upper block rotation");
                for (int slot = 0; slot < 4; slot++) {
                    checkEquals(Items.PAPER, upper.getItems().getStackInSlot(slot).getItem(), "Upper paper position");
                    checkEquals(3 - slot, upper.getCubeRotation(slot), "Upper item rotation");
                }
                checkEquals(Items.GLOWSTONE, upper.getItems().getStackInSlot(4).getItem(), "Pending upper item");
                checkEquals(1, upper.getCubeRotation(4), "Pending upper rotation");
                checkEquals(1, GameTestScaffold.count(lower.getItems(), Items.GLOWSTONE), "Lower conservation");
                checkEquals(1, GameTestScaffold.count(upper.getItems(), Items.GLOWSTONE), "Upper glowstone conservation");
                checkEquals(4, GameTestScaffold.count(upper.getItems(), Items.PAPER), "Upper paper conservation");
                checkEquals(1, droppedNear(helper, middle.getBlockPos(), Items.DIAMOND), "Final middle drops");
                checkEquals(3, lower.getBlockState().getValue(SinglesStackBlock.LIGHT_LEVEL), "Lower light");
                checkEquals(3, upper.getBlockState().getValue(SinglesStackBlock.LIGHT_LEVEL), "Upper light");
                checkEquals(1, GameTestScaffold.comparatorOutput(helper, lowerComparator), "Lower comparator");
                checkEquals(2, GameTestScaffold.comparatorOutput(helper, upperComparator), "Upper comparator");
                helper.succeed();
            });
        });
    }

    /**
     * To reproduce: select Singles, V-place a stick on a floor, then V-deposit into an unsupported
     * second-layer cell in another visual column. The bottom cell accepts one stick; the
     * unsupported cell refuses it and leaves the hand unchanged.
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
     * To reproduce: V-deposit a stick into a bottom-layer Singles cell, then paper directly above
     * it in the same visual column. Both deposits succeed; the paper rests on the stick.
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
     * The fixture uses lower and upper block rotations of one and three quarter-turns and directly
     * fills the lower top cell in visual column 6.
     *
     * <p>To check manually: build a Singles column to the top layer, place a Singles block above
     * it, and Shift-right-click each block with a redstone torch to give them different rotations.
     * Deposit into an empty upper bottom cell directly above an occupied lower top cell. Support
     * follows the visible column.
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
     * To reproduce: stack a stick, paper, and apple in three consecutive cells of one Singles
     * column. Shift-right-click paper once and apple three times with a soul torch. Release Shift
     * and plain-right-click the bottom stick with an empty hand. Paper and apple each fall one
     * layer with their item rotations unchanged; the vacated top cell is empty.
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
     * Synthetic admission fixture: an apple supports a directly seeded item currently accepted by
     * Bar and refused by Singles. Extracting the lower item must conserve the refused item, counted
     * in storage plus nearby drops; the test does not change policy.
     *
     * <p>To check a policy change manually, remove the ingot-tag patterns with /ss ingot remove so
     * a chosen ingot can enter Singles, deposit it above an apple, then restore its pattern with
     * /ss ingot add. Extract the apple; the stored ingot is conserved despite being refused for new
     * Singles deposits. Save the original tag list and restore it afterward.
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
     * Synthetic admission fixture: the lower block holds an apple and the upper block has a
     * directly seeded bottom item currently refused by Singles. Extraction exercises cross-block
     * draw-down; it asserts conservation across both inventories and nearby drops, without changing
     * policy.
     *
     * <p>For a visible policy-change check, temporarily remove the chosen ingot's tag patterns with
     * /ss ingot remove, build a supported column spanning two Singles blocks with that ingot above
     * the seam, then restore the patterns with /ss ingot add. Extract below the seam and confirm
     * the ingot is conserved. Restore the original list afterward.
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
     * The fixture directly stores a stick at the bottom and paper two layers above in visual column
     * 2, leaving the middle cell empty. Extracting the stick shifts paper down one layer and leaves
     * the bottom empty.
     *
     * <p>This unsupported starting layout requires a fixture or /data merge block rather than
     * ordinary deposits. Create a Singles block with /setblock, then merge
     * {Items:{Size:64,Items:[{Slot:2,id:"minecraft:stick",Count:1b},{Slot:34,id:"minecraft:paper",Count:1b}]}}
     * into its block entity. Plain-right-click the stick; paper moves to the middle cell,
     * preserving the gap.
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
     * The fixture directly seeds a lower top stick and an upper bottom paper in visual column 9,
     * with block rotations of one and two quarter-turns and paper rotation of three quarter-turns.
     *
     * <p>To check manually: build a supported Singles column across two differently rotated blocks,
     * rotate the upper bottom item with a soul torch, and extract the lower top item with an empty
     * hand. The upper item crosses the visible seam with its facing unchanged; an emptied upper
     * block disappears.
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
     * Internal ray-selection check: a north-to-south ray through the lowest west-side row meets an
     * occupied cell at slot 8. The trace selects the preceding empty cell, slot 4.
     *
     * <p>To check the visible targeting, deposit a stick at the far side of that bottom row, then
     * aim from the north through its empty cells while V-depositing. The nearest empty cell before
     * the occupied stick receives the item.
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
     * Internal ray-selection check: a north-to-south ray through the lowest west-side row of an
     * empty Singles block selects its farthest cell, slot 12.
     *
     * <p>To check manually, V-place Singles against a floor while aiming through an empty bottom
     * row from the north. The first item appears in the farthest cell along the server view ray.
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
     * A ray entering the block's lowest north-west cell head on and running clear through it, so the
     * cells it meets are the column the trace is being asked about.
     */
    private static ViewRay traceThroughBlock(BlockPos blockPos) {
        double x = blockPos.getX() + 0.125;
        double y = blockPos.getY() + 0.125;
        return new ViewRay(new Vec3(x, y, blockPos.getZ() - 1.0), new Vec3(x, y, blockPos.getZ() + 2.0));
    }

    /**
     * The fixture checks an empty Singles block, one occupied cell, then all 64 cells: signals are
     * 0, 1, and 15.
     *
     * <p>To reproduce visible readings, place an outward-facing comparator beside Singles, deposit
     * one item, then fill all cells. Read 1 and 15; after extracting all items, the block
     * disappears and the comparator reads 0.
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
     * Internal layout check: a full lower Singles block and empty contiguous upper block both
     * report 8. The empty upper block is temporary, so the exact layout is not a stable manual
     * setup.
     *
     * <p>To check whole-run readings, build two contiguous partly filled Singles blocks and put an
     * outward-facing comparator on a solid support beside each. Both report the same fill after
     * updates.
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
     * The fixture fills a lower Singles block beneath an empty upper block, checks signal 8,
     * removes the upper block, and checks 15.
     *
     * <p>To reproduce the visible capacity change, fill a lower Singles block and place one item in
     * the supported block above. The comparator reads 8. Extract the upper item; its empty block
     * disappears and the remaining full block reads 15.
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
