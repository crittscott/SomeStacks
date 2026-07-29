package com.github.crittscott.somestacks.gametest;

import com.github.crittscott.somestacks.SomeStacks;
import com.github.crittscott.somestacks.block.SinglesStackBE;
import com.github.crittscott.somestacks.util.SinglesCubeIdx;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.items.IItemHandler;

import static com.github.crittscott.somestacks.gametest.GameTestSupport.ORIGIN;
import static com.github.crittscott.somestacks.gametest.GameTestSupport.check;
import static com.github.crittscott.somestacks.gametest.GameTestSupport.checkEquals;

@GameTestHolder(SomeStacks.MODID)
@PrefixGameTestTemplate(false)
public final class SinglesColumnGameTests {
    private SinglesColumnGameTests() {}

    @GameTest(template = GameTestSupport.TEMPLATE)
    public static void standaloneBottomIsGroundedAndFloatingCellIsRejected(GameTestHelper helper) {
        SinglesStackBE singles = GameTestSupport.placeSingles(helper, ORIGIN);
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

    @GameTest(template = GameTestSupport.TEMPLATE)
    public static void upperCellUsesTheCellImmediatelyBelow(GameTestHelper helper) {
        SinglesStackBE singles = GameTestSupport.placeSingles(helper, ORIGIN);
        int column = 5;
        int bottom = SinglesCubeIdx.indexFromColumn(column, 0);
        int secondLayer = SinglesCubeIdx.indexFromColumn(column, 1);

        check(singles.depositAt(bottom, new ItemStack(Items.STICK)),
                "Bottom deposit failed");
        check(singles.depositAt(secondLayer, new ItemStack(Items.PAPER)),
                "Supported upper deposit failed");
        helper.succeed();
    }

    @GameTest(template = GameTestSupport.TEMPLATE)
    public static void differentlyRotatedBlocksShareVisualSeamColumns(GameTestHelper helper) {
        SinglesStackBE lower = GameTestSupport.placeSingles(helper, ORIGIN);
        SinglesStackBE upper = GameTestSupport.placeSingles(helper, ORIGIN.above());
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

    @GameTest(template = GameTestSupport.TEMPLATE)
    public static void extractionShiftsOneColumnAndCarriesItemRotation(GameTestHelper helper) {
        SinglesStackBE singles = GameTestSupport.placeSingles(helper, ORIGIN);
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
     */
    @GameTest(template = GameTestSupport.TEMPLATE)
    public static void shiftKeepsAnItemItWouldNoLongerAccept(GameTestHelper helper) {
        SinglesStackBE singles = GameTestSupport.placeSingles(helper, ORIGIN);
        BlockPos pos = singles.getBlockPos();
        Item legacy = GameTestSupport.firstBarItem();
        check(!SinglesStackBE.isValidSinglesItem(new ItemStack(legacy)),
                "Test needs an item a Singles Stack refuses");
        int bottom = SinglesCubeIdx.indexFromColumn(0, 0);
        int second = SinglesCubeIdx.indexFromColumn(0, 1);
        singles.getItems().insertItem(bottom, new ItemStack(Items.APPLE), false);
        GameTestSupport.seedSlot(singles.getItems(), second, new ItemStack(legacy));

        ItemStack extracted = singles.extractAt(bottom);

        checkEquals(Items.APPLE, extracted.getItem(), "Extracted item");
        int accounted = GameTestSupport.heldAt(helper, pos, legacy)
                + GameTestSupport.droppedNear(helper, pos, legacy);
        checkEquals(1, accounted, "Refused item after the shift that moved it");
        helper.succeed();
    }

    /**
     * The same conservation across a block boundary: the item handed down from the block above is
     * one the receiving block would refuse from a deposit.
     */
    @GameTest(template = GameTestSupport.TEMPLATE)
    public static void drawDownKeepsAnItemItWouldNoLongerAccept(GameTestHelper helper) {
        SinglesStackBE lower = GameTestSupport.placeSingles(helper, ORIGIN);
        SinglesStackBE upper = GameTestSupport.placeSingles(helper, ORIGIN.above());
        BlockPos lowerPos = lower.getBlockPos();
        BlockPos upperPos = upper.getBlockPos();
        Item legacy = GameTestSupport.firstBarItem();
        check(!SinglesStackBE.isValidSinglesItem(new ItemStack(legacy)),
                "Test needs an item a Singles Stack refuses");
        int bottom = SinglesCubeIdx.indexFromColumn(0, 0);
        lower.getItems().insertItem(bottom, new ItemStack(Items.APPLE), false);
        GameTestSupport.seedSlot(upper.getItems(), bottom, new ItemStack(legacy));

        ItemStack extracted = lower.extractAt(bottom);

        checkEquals(Items.APPLE, extracted.getItem(), "Extracted item");
        int accounted = GameTestSupport.heldAt(helper, lowerPos, legacy)
                + GameTestSupport.heldAt(helper, upperPos, legacy)
                + GameTestSupport.droppedNear(helper, lowerPos, legacy);
        checkEquals(1, accounted, "Refused item after being drawn down across the seam");
        helper.succeed();
    }

    @GameTest(template = GameTestSupport.TEMPLATE)
    public static void extractionPreservesAnExistingGap(GameTestHelper helper) {
        SinglesStackBE singles = GameTestSupport.placeSingles(helper, ORIGIN);
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

    @GameTest(template = GameTestSupport.TEMPLATE)
    public static void extractionDrawsAcrossDifferentlyRotatedBlockBoundary(GameTestHelper helper) {
        SinglesStackBE lower = GameTestSupport.placeSingles(helper, ORIGIN);
        SinglesStackBE upper = GameTestSupport.placeSingles(helper, ORIGIN.above());
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

    @GameTest(template = GameTestSupport.TEMPLATE)
    public static void capabilityInsertionIgnoresRequestedSlotAndFillsLowestCells(GameTestHelper helper) {
        SinglesStackBE singles = GameTestSupport.placeSingles(helper, ORIGIN);
        IItemHandler capability = GameTestSupport.capability(singles);
        ItemStack offered = new ItemStack(Items.STICK, 3);

        ItemStack simulated = capability.insertItem(63, offered, true);
        check(simulated.isEmpty(), "Simulation did not accept three items");
        checkEquals(3, offered.getCount(), "Simulation changed input");
        checkEquals(0, GameTestSupport.occupied(singles.getItems()),
                "Simulation changed occupancy");

        ItemStack remainder = capability.insertItem(63, offered, false);

        check(remainder.isEmpty(), "Committed insertion left a remainder");
        checkEquals(3, offered.getCount(), "Capability mutated caller input");
        checkEquals(Items.STICK, singles.getItems().getStackInSlot(0).getItem(),
                "First lowest cell");
        checkEquals(Items.STICK, singles.getItems().getStackInSlot(1).getItem(),
                "Second lowest cell");
        checkEquals(Items.STICK, singles.getItems().getStackInSlot(2).getItem(),
                "Third lowest cell");
        helper.succeed();
    }

    @GameTest(template = GameTestSupport.TEMPLATE)
    public static void capabilityExtractionUsesThePlayerGravityPath(GameTestHelper helper) {
        SinglesStackBE singles = GameTestSupport.placeSingles(helper, ORIGIN);
        int column = 0;
        int bottom = SinglesCubeIdx.indexFromColumn(column, 0);
        int above = SinglesCubeIdx.indexFromColumn(column, 1);
        singles.getItems().insertItem(bottom, new ItemStack(Items.STICK), false);
        singles.getItems().insertItem(above, new ItemStack(Items.PAPER), false);
        IItemHandler capability = GameTestSupport.capability(singles);

        ItemStack simulated = capability.extractItem(bottom, 64, true);
        checkEquals(Items.STICK, simulated.getItem(), "Simulated extracted item");
        checkEquals(Items.STICK, singles.getItems().getStackInSlot(bottom).getItem(),
                "Simulation mutated contents");

        ItemStack extracted = capability.extractItem(bottom, 64, false);

        checkEquals(1, extracted.getCount(), "Capability extracted more than one item");
        checkEquals(Items.PAPER, singles.getItems().getStackInSlot(bottom).getItem(),
                "Capability extraction did not shift column");
        helper.succeed();
    }

    @GameTest(template = GameTestSupport.TEMPLATE)
    public static void depositTraceStopsAtLastEmptyBeforeOccupiedCell(GameTestHelper helper) {
        SinglesStackBE singles = GameTestSupport.placeSingles(helper, ORIGIN);
        singles.getItems().insertItem(8, new ItemStack(Items.STICK), false);
        BlockPos blockPos = singles.getBlockPos();

        int result = SinglesCubeIdx.traceAllPositions(
                new Vec3(blockPos.getX() + 0.125, blockPos.getY() + 0.125,
                        blockPos.getZ() - 1.0),
                new Vec3(0.0, 0.0, 1.0),
                blockPos,
                singles.getItems(),
                0);

        checkEquals(4, result, "Trace deposit index");
        helper.succeed();
    }

    @GameTest(template = GameTestSupport.TEMPLATE)
    public static void depositTraceUsesFarthestCellWhenNothingIsOccupied(GameTestHelper helper) {
        SinglesStackBE singles = GameTestSupport.placeSingles(helper, ORIGIN);
        BlockPos blockPos = singles.getBlockPos();

        int result = SinglesCubeIdx.traceAllPositions(
                new Vec3(blockPos.getX() + 0.125, blockPos.getY() + 0.125,
                        blockPos.getZ() - 1.0),
                new Vec3(0.0, 0.0, 1.0),
                blockPos,
                singles.getItems(),
                0);

        checkEquals(12, result, "Trace deposit index");
        helper.succeed();
    }
}
