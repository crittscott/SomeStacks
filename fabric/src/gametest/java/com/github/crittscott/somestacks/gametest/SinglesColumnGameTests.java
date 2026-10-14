package com.github.crittscott.somestacks.gametest;

import com.github.crittscott.somestacks.block.SinglesStackBE;
import com.github.crittscott.somestacks.util.SinglesCubeIdx;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.fabricmc.fabric.api.transfer.v1.item.ItemVariant;
import net.fabricmc.fabric.api.transfer.v1.storage.SlottedStorage;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import static com.github.crittscott.somestacks.gametest.GameTestScaffold.ORIGIN;
import static com.github.crittscott.somestacks.gametest.GameTestScaffold.check;
import static com.github.crittscott.somestacks.gametest.GameTestScaffold.checkEquals;
import static com.github.crittscott.somestacks.gametest.GameTestScaffold.occupied;
import static com.github.crittscott.somestacks.gametest.GameTestScaffold.placeSingles;

/**
 * Singles support and gravity in a live level: grounding at the bottom of a column, a cell resting
 * on the one below it, visual columns lining up across differently rotated blocks, and extraction
 * shifting a column down while carrying each item's rotation with it.
 */
public final class SinglesColumnGameTests implements FabricGameTest {
    /**
     * Loader entry point for {@link
     * SinglesColumnChecks#standaloneBottomIsGroundedAndFloatingCellIsRejected}; see that method for
     * the scenario, manual check, and any internal-only assertions.
     */
    @GameTest(template = FabricGameTestSupport.TEMPLATE)
    public void standaloneBottomIsGroundedAndFloatingCellIsRejected(GameTestHelper helper) {
        SinglesColumnChecks.standaloneBottomIsGroundedAndFloatingCellIsRejected(helper);
    }

    /**
     * Loader entry point for {@link SinglesColumnChecks#upperCellUsesTheCellImmediatelyBelow}; see
     * that method for
     * the scenario, manual check, and any internal-only assertions.
     */
    @GameTest(template = FabricGameTestSupport.TEMPLATE)
    public void upperCellUsesTheCellImmediatelyBelow(GameTestHelper helper) {
        SinglesColumnChecks.upperCellUsesTheCellImmediatelyBelow(helper);
    }

    /**
     * Loader entry point for {@link
     * SinglesColumnChecks#differentlyRotatedBlocksShareVisualSeamColumns}; see that method for
     * the scenario, manual check, and any internal-only assertions.
     */
    @GameTest(template = FabricGameTestSupport.TEMPLATE)
    public void differentlyRotatedBlocksShareVisualSeamColumns(GameTestHelper helper) {
        SinglesColumnChecks.differentlyRotatedBlocksShareVisualSeamColumns(helper);
    }

    /**
     * Loader entry point for {@link
     * SinglesColumnChecks#extractionShiftsOneColumnAndCarriesItemRotation}; see that method for
     * the scenario, manual check, and any internal-only assertions.
     */
    @GameTest(template = FabricGameTestSupport.TEMPLATE)
    public void extractionShiftsOneColumnAndCarriesItemRotation(GameTestHelper helper) {
        SinglesColumnChecks.extractionShiftsOneColumnAndCarriesItemRotation(helper);
    }

    /**
     * Loader entry point for {@link SinglesColumnChecks#shiftKeepsAnItemItWouldNoLongerAccept}; see
     * that method for
     * the scenario, manual check, and any internal-only assertions.
     */
    @GameTest(template = FabricGameTestSupport.TEMPLATE)
    public void shiftKeepsAnItemItWouldNoLongerAccept(GameTestHelper helper) {
        SinglesColumnChecks.shiftKeepsAnItemItWouldNoLongerAccept(helper);
    }

    /**
     * Loader entry point for {@link SinglesColumnChecks#drawDownKeepsAnItemItWouldNoLongerAccept};
     * see that method for
     * the scenario, manual check, and any internal-only assertions.
     */
    @GameTest(template = FabricGameTestSupport.TEMPLATE)
    public void drawDownKeepsAnItemItWouldNoLongerAccept(GameTestHelper helper) {
        SinglesColumnChecks.drawDownKeepsAnItemItWouldNoLongerAccept(helper);
    }

    /**
     * Loader entry point for {@link SinglesColumnChecks#extractionPreservesAnExistingGap}; see that
     * method for
     * the scenario, manual check, and any internal-only assertions.
     */
    @GameTest(template = FabricGameTestSupport.TEMPLATE)
    public void extractionPreservesAnExistingGap(GameTestHelper helper) {
        SinglesColumnChecks.extractionPreservesAnExistingGap(helper);
    }

    /**
     * Loader entry point for {@link
     * SinglesColumnChecks#extractionDrawsAcrossDifferentlyRotatedBlockBoundary}; see that method
     * for
     * the scenario, manual check, and any internal-only assertions.
     */
    @GameTest(template = FabricGameTestSupport.TEMPLATE)
    public void extractionDrawsAcrossDifferentlyRotatedBlockBoundary(GameTestHelper helper) {
        SinglesColumnChecks.extractionDrawsAcrossDifferentlyRotatedBlockBoundary(helper);
    }

    /**
     * Internal positional insertion check: offer three sticks to unsupported Singles slot 63, then
     * grounded slot 0. Slot 63 refuses all three; slot 0 accepts exactly one without mutating the
     * caller's offer. Simulation stores nothing.
     *
     * <p>To check manually, use a slot-addressing machine on an empty Singles fixture: an
     * unsupported target must not redirect, and a grounded target stores one stick. A dry-run
     * assertion requires automation that exposes simulation or abort.
     */
    @GameTest(template = FabricGameTestSupport.TEMPLATE)
    public void capabilityInsertionAnswersForTheCellItIsGiven(GameTestHelper helper) {
        SinglesStackBE singles = placeSingles(helper, ORIGIN);
        SlottedStorage<ItemVariant> capability = FabricGameTestSupport.capability(singles);
        ItemStack offered = new ItemStack(Items.STICK, 3);

        // Cell 63 is a top-layer cell with nothing beneath it, so it is refused rather than
        // redirected to a cell that would take the item.
        checkEquals(3, FabricGameTestSupport.insertAt(capability, 63, offered, true).getCount(),
                "Simulated insertion into an unsupported cell");
        checkEquals(3, FabricGameTestSupport.insertAt(capability, 63, offered, false).getCount(),
                "Committed insertion into an unsupported cell");
        checkEquals(0, occupied(singles.getItems()),
                "An unsupported cell stored something");

        // Cell 0 is grounded outright, and takes exactly the one item a cell holds.
        checkEquals(2, FabricGameTestSupport.insertAt(capability, 0, offered, true).getCount(),
                "Simulated insertion into a grounded cell");
        checkEquals(3, offered.getCount(), "Simulation changed input");
        checkEquals(0, occupied(singles.getItems()),
                "Simulation changed occupancy");

        checkEquals(2, FabricGameTestSupport.insertAt(capability, 0, offered, false).getCount(),
                "Committed insertion into a grounded cell");
        checkEquals(3, offered.getCount(), "Capability mutated caller input");
        checkEquals(1, occupied(singles.getItems()),
                "One call should store one item");
        checkEquals(Items.STICK, singles.getItems().getStackInSlot(0).getItem(),
                "The cell named should hold the item");
        helper.succeed();
    }

    /**
     * To reproduce: offer three sticks to an empty Singles fixture through a machine that visits
     * slots in ascending order. The first three bottom-layer cells receive one stick each and
     * nothing remains uninserted. The test also asserts that the adapter does not mutate its
     * caller-owned offer.
     */
    @GameTest(template = FabricGameTestSupport.TEMPLATE)
    public void capabilityWalkFillsTheColumnFromTheBottom(GameTestHelper helper) {
        SinglesStackBE singles = placeSingles(helper, ORIGIN);
        SlottedStorage<ItemVariant> capability = FabricGameTestSupport.capability(singles);
        ItemStack offered = new ItemStack(Items.STICK, 3);

        // A caller walking the advertised cells in order still fills the column, because each
        // placement stands before the next cell is offered.
        ItemStack remainder = FabricGameTestSupport.insertWalkingSlots(capability, offered, false);

        check(remainder.isEmpty(), "A walk of the cells left a remainder");
        checkEquals(3, offered.getCount(), "Capability mutated caller input");
        for (int cell = 0; cell < 3; cell++) {
            checkEquals(Items.STICK, singles.getItems().getStackInSlot(cell).getItem(),
                    "Lowest cell " + cell);
        }
        helper.succeed();
    }

    /**
     * To reproduce: put a stick at the bottom of one Singles column and paper directly above.
     * Automate extraction from the stick's slot. Exactly one stick is extracted and paper shifts
     * into its cell. The test also performs a dry run first, which returns a stick without changing
     * contents; this requires a machine or probe with simulation/abort support.
     */
    @GameTest(template = FabricGameTestSupport.TEMPLATE)
    public void capabilityExtractionUsesThePlayerGravityPath(GameTestHelper helper) {
        SinglesStackBE singles = placeSingles(helper, ORIGIN);
        int column = 0;
        int bottom = SinglesCubeIdx.indexFromColumn(column, 0);
        int above = SinglesCubeIdx.indexFromColumn(column, 1);
        singles.getItems().insertItem(bottom, new ItemStack(Items.STICK), false);
        singles.getItems().insertItem(above, new ItemStack(Items.PAPER), false);
        SlottedStorage<ItemVariant> capability = FabricGameTestSupport.capability(singles);

        ItemStack simulated = FabricGameTestSupport.extractAt(capability, bottom, 64, true);
        checkEquals(Items.STICK, simulated.getItem(), "Simulated extracted item");
        checkEquals(Items.STICK, singles.getItems().getStackInSlot(bottom).getItem(),
                "Simulation mutated contents");

        ItemStack extracted = FabricGameTestSupport.extractAt(capability, bottom, 64, false);

        checkEquals(1, extracted.getCount(), "Capability extracted more than one item");
        checkEquals(Items.PAPER, singles.getItems().getStackInSlot(bottom).getItem(),
                "Capability extraction did not shift column");
        helper.succeed();
    }

    /**
     * Loader entry point for {@link
     * SinglesColumnChecks#depositTraceStopsAtLastEmptyBeforeOccupiedCell}; see that method for
     * the scenario, manual check, and any internal-only assertions.
     */
    @GameTest(template = FabricGameTestSupport.TEMPLATE)
    public void depositTraceStopsAtLastEmptyBeforeOccupiedCell(GameTestHelper helper) {
        SinglesColumnChecks.depositTraceStopsAtLastEmptyBeforeOccupiedCell(helper);
    }

    /**
     * Loader entry point for {@link
     * SinglesColumnChecks#depositTraceUsesFarthestCellWhenNothingIsOccupied}; see that method for
     * the scenario, manual check, and any internal-only assertions.
     */
    @GameTest(template = FabricGameTestSupport.TEMPLATE)
    public void depositTraceUsesFarthestCellWhenNothingIsOccupied(GameTestHelper helper) {
        SinglesColumnChecks.depositTraceUsesFarthestCellWhenNothingIsOccupied(helper);
    }

    /**
     * Loader entry point for {@link SinglesColumnChecks#comparatorReservesZeroForAnEmptyColumn};
     * see that method for
     * the scenario, manual check, and any internal-only assertions.
     */
    @GameTest(template = FabricGameTestSupport.TEMPLATE)
    public void comparatorReservesZeroForAnEmptyColumn(GameTestHelper helper) {
        SinglesColumnChecks.comparatorReservesZeroForAnEmptyColumn(helper);
    }

    /**
     * Loader entry point for {@link
     * SinglesColumnChecks#comparatorReadsTheWholeColumnFromEveryBlock}; see that method for
     * the scenario, manual check, and any internal-only assertions.
     */
    @GameTest(template = FabricGameTestSupport.TEMPLATE)
    public void comparatorReadsTheWholeColumnFromEveryBlock(GameTestHelper helper) {
        SinglesColumnChecks.comparatorReadsTheWholeColumnFromEveryBlock(helper);
    }

    /**
     * Loader entry point for {@link SinglesColumnChecks#comparatorFollowsAColumnLosingABlock}; see
     * that method for
     * the scenario, manual check, and any internal-only assertions.
     */
    @GameTest(template = FabricGameTestSupport.TEMPLATE)
    public void comparatorFollowsAColumnLosingABlock(GameTestHelper helper) {
        SinglesColumnChecks.comparatorFollowsAColumnLosingABlock(helper);
    }

    /** See {@link SinglesColumnChecks#middleRemovalPreservesAndPublishesBothSurvivingColumns} for the split scenario and manual procedure. */
    @GameTest(template = FabricGameTestSupport.TEMPLATE)
    public void middleRemovalPreservesAndPublishesBothSurvivingColumns(GameTestHelper helper) {
        SinglesColumnChecks.middleRemovalPreservesAndPublishesBothSurvivingColumns(helper);
    }
}
