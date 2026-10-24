package com.github.crittscott.somestacks.gametest;

import com.github.crittscott.somestacks.block.BarStackBE;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.fabricmc.fabric.api.transfer.v1.item.ItemVariant;
import net.fabricmc.fabric.api.transfer.v1.storage.SlottedStorage;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import static com.github.crittscott.somestacks.gametest.GameTestScaffold.ORIGIN;
import static com.github.crittscott.somestacks.gametest.GameTestScaffold.check;
import static com.github.crittscott.somestacks.gametest.GameTestScaffold.checkEquals;
import static com.github.crittscott.somestacks.gametest.GameTestScaffold.count;
import static com.github.crittscott.somestacks.gametest.GameTestScaffold.droppedNear;
import static com.github.crittscott.somestacks.gametest.GameTestScaffold.firstBarItem;
import static com.github.crittscott.somestacks.gametest.GameTestScaffold.heldAt;
import static com.github.crittscott.somestacks.gametest.GameTestScaffold.occupied;
import static com.github.crittscott.somestacks.gametest.GameTestScaffold.placeBar;
import static com.github.crittscott.somestacks.gametest.GameTestScaffold.seedSlot;

/**
 * Bar support and collapse in a live level: item validity, grounding at the bottom of a column,
 * footprint overlap deciding support across the alternating layer orientations, and the difference
 * between player extraction, which drops what it unsupports, and automated extraction, which
 * backfills from the top instead.
 */
public final class BarColumnGameTests implements FabricGameTest {
    /** See {@link BarColumnChecks#validityAndBottomGroundingAreEnforced}. */
    @GameTest(template = FabricGameTestSupport.TEMPLATE)
    public void validityAndBottomGroundingAreEnforced(GameTestHelper helper) {
        BarColumnChecks.validityAndBottomGroundingAreEnforced(helper);
    }

    /** See {@link BarColumnChecks#bricksAreBarItemsButBrickBlocksAreNot}. */
    @GameTest(template = FabricGameTestSupport.TEMPLATE)
    public void bricksAreBarItemsButBrickBlocksAreNot(GameTestHelper helper) {
        BarColumnChecks.bricksAreBarItemsButBrickBlocksAreNot(helper);
    }

    /** See {@link BarColumnChecks#upperBarRequiresOverlappingSupport}. */
    @GameTest(template = FabricGameTestSupport.TEMPLATE)
    public void upperBarRequiresOverlappingSupport(GameTestHelper helper) {
        BarColumnChecks.upperBarRequiresOverlappingSupport(helper);
    }

    /** See {@link BarColumnChecks#playerExtractionDropsUnsupportedBars}. */
    @GameTest(template = FabricGameTestSupport.TEMPLATE)
    public void playerExtractionDropsUnsupportedBars(GameTestHelper helper) {
        BarColumnChecks.playerExtractionDropsUnsupportedBars(helper);
    }

    /** See {@link BarColumnChecks#supportedNeighborSurvivesPlayerExtraction}. */
    @GameTest(template = FabricGameTestSupport.TEMPLATE)
    public void supportedNeighborSurvivesPlayerExtraction(GameTestHelper helper) {
        BarColumnChecks.supportedNeighborSurvivesPlayerExtraction(helper);
    }

    /** See {@link BarColumnChecks#differentlyOrientedSeamUsesFootprintOverlap}. */
    @GameTest(template = FabricGameTestSupport.TEMPLATE)
    public void differentlyOrientedSeamUsesFootprintOverlap(GameTestHelper helper) {
        BarColumnChecks.differentlyOrientedSeamUsesFootprintOverlap(helper);
    }

    /** See {@link BarColumnChecks#depositTraceStopsAtLastEmptyBeforeOccupiedBar}. */
    @GameTest(template = FabricGameTestSupport.TEMPLATE)
    public void depositTraceStopsAtLastEmptyBeforeOccupiedBar(GameTestHelper helper) {
        BarColumnChecks.depositTraceStopsAtLastEmptyBeforeOccupiedBar(helper);
    }

    /**
     * To reproduce in-game: fill a supported Bar column, let item automation extract a lower
     * position, and verify the top bar fills the hole without dropping an item.
     */
    @GameTest(template = FabricGameTestSupport.TEMPLATE)
    public void automationBackfillsWithoutDropping(GameTestHelper helper) {
        AutomationChecks.barAutomationBackfillsWithoutDropping(helper);
    }

    /**
     * To reproduce in-game: attach item automation to a Bar Stack and target individual advertised
     * positions; unsupported positions refuse insertion while a supported empty position accepts it.
     */
    @GameTest(template = FabricGameTestSupport.TEMPLATE)
    public void storageInsertionAnswersForThePositionItIsGiven(GameTestHelper helper) {
        AutomationChecks.barInsertionAnswersForThePositionItIsGiven(helper);
    }

    /**
     * Validity gates insertion only, so a bar stored before a data pack
     * reload narrowed the rule has to survive the backfill an automated extraction moves it through.
     * A stick stands in for such a bar: a Bar Stack refuses one from a deposit, but may be holding
     * contents the current ingot set no longer covers.
     *
     * <p>To reproduce in-game: fill a Bar Stack, narrow the ingot rule so one stored bar is no
     * longer accepted, then automate extraction below it. Backfill retains or drops that legacy
     * bar without deleting or changing it.
     */
    @GameTest(template = FabricGameTestSupport.TEMPLATE)
    public void backfillKeepsABarItWouldNoLongerAccept(GameTestHelper helper) {
        AutomationChecks.barBackfillKeepsAStoredItemNoLongerAccepted(helper);
    }

    /**
     * No in-game reproduction applies: this verifies the Fabric transaction simulation contract; a
     * simulated Bar extraction reports the result without changing the stored bar.
     */
    @GameTest(template = FabricGameTestSupport.TEMPLATE)
    public void storageSimulationDoesNotMutateExtraction(GameTestHelper helper) {
        BarStackBE bars = placeBar(helper, ORIGIN);
        Item barItem = firstBarItem();
        bars.depositAt(0, new ItemStack(barItem));
        SlottedStorage<ItemVariant> storage = FabricGameTestSupport.storage(bars);

        ItemStack simulated = FabricGameTestSupport.extractAt(storage, 0, 64, true);

        checkEquals(barItem, simulated.getItem(), "Simulated item");
        checkEquals(1, simulated.getCount(), "Simulated count");
        checkEquals(barItem, bars.getItems().getStackInSlot(0).getItem(),
                "Simulation mutated contents");
        helper.succeed();
    }

    /** See {@link BarColumnChecks#breakingLowerBlockCollapsesDependentUpperBlock}. */
    @GameTest(template = FabricGameTestSupport.TEMPLATE)
    public void breakingLowerBlockCollapsesDependentUpperBlock(GameTestHelper helper) {
        BarColumnChecks.breakingLowerBlockCollapsesDependentUpperBlock(helper);
    }

    /** See {@link BarColumnChecks#breakingFullColumnConsolidatesDrops}. */
    @GameTest(template = FabricGameTestSupport.TEMPLATE)
    public void breakingFullColumnConsolidatesDrops(GameTestHelper helper) {
        BarColumnChecks.breakingFullColumnConsolidatesDrops(helper);
    }

    /** See {@link BarColumnChecks#playerCascadeConsolidatesAcrossBlocks}. */
    @GameTest(template = FabricGameTestSupport.TEMPLATE)
    public void playerCascadeConsolidatesAcrossBlocks(GameTestHelper helper) {
        BarColumnChecks.playerCascadeConsolidatesAcrossBlocks(helper);
    }

    /** See {@link BarColumnChecks#collapseDoesNotMergeDifferentComponents}. */
    @GameTest(template = FabricGameTestSupport.TEMPLATE)
    public void collapseDoesNotMergeDifferentComponents(GameTestHelper helper) {
        BarColumnChecks.collapseDoesNotMergeDifferentComponents(helper);
    }

    /** See {@link BarColumnChecks#comparatorReservesZeroForAnEmptyColumn}. */
    @GameTest(template = FabricGameTestSupport.TEMPLATE)
    public void comparatorReservesZeroForAnEmptyColumn(GameTestHelper helper) {
        BarColumnChecks.comparatorReservesZeroForAnEmptyColumn(helper);
    }

    /** See {@link BarColumnChecks#comparatorReadsTheWholeColumnFromEveryBlock}. */
    @GameTest(template = FabricGameTestSupport.TEMPLATE)
    public void comparatorReadsTheWholeColumnFromEveryBlock(GameTestHelper helper) {
        BarColumnChecks.comparatorReadsTheWholeColumnFromEveryBlock(helper);
    }

    /** See {@link BarColumnChecks#comparatorFollowsAColumnLosingABlock}. */
    @GameTest(template = FabricGameTestSupport.TEMPLATE)
    public void comparatorFollowsAColumnLosingABlock(GameTestHelper helper) {
        BarColumnChecks.comparatorFollowsAColumnLosingABlock(helper);
    }
}
