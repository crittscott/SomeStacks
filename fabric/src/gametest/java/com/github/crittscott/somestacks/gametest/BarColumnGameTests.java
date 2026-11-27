package com.github.crittscott.somestacks.gametest;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;

/**
 * Bar support and collapse in a live level: item validity, grounding at the bottom of a column,
 * footprint overlap deciding support across the alternating layer orientations, and the difference
 * between player extraction, which drops what it unsupports, and automated extraction, which
 * backfills from the top instead.
 */
public final class BarColumnGameTests {
    /** See {@link BarColumnChecks#validityAndBottomGroundingAreEnforced}. */
    @GameTest(structure = FabricGameTestSupport.TEMPLATE, maxTicks = 100)
    public void validityAndBottomGroundingAreEnforced(GameTestHelper helper) {
        BarColumnChecks.validityAndBottomGroundingAreEnforced(helper);
    }

    /** See {@link BarColumnChecks#bricksAreBarItemsButBrickBlocksAreNot}. */
    @GameTest(structure = FabricGameTestSupport.TEMPLATE, maxTicks = 100)
    public void bricksAreBarItemsButBrickBlocksAreNot(GameTestHelper helper) {
        BarColumnChecks.bricksAreBarItemsButBrickBlocksAreNot(helper);
    }

    /** See {@link BarColumnChecks#upperBarRequiresOverlappingSupport}. */
    @GameTest(structure = FabricGameTestSupport.TEMPLATE, maxTicks = 100)
    public void upperBarRequiresOverlappingSupport(GameTestHelper helper) {
        BarColumnChecks.upperBarRequiresOverlappingSupport(helper);
    }

    /** See {@link BarColumnChecks#playerExtractionDropsUnsupportedBars}. */
    @GameTest(structure = FabricGameTestSupport.TEMPLATE, maxTicks = 100)
    public void playerExtractionDropsUnsupportedBars(GameTestHelper helper) {
        BarColumnChecks.playerExtractionDropsUnsupportedBars(
                helper, FabricGameTestSupport.playerFactory(helper));
    }

    /** See {@link BarColumnChecks#supportedNeighborSurvivesPlayerExtraction}. */
    @GameTest(structure = FabricGameTestSupport.TEMPLATE, maxTicks = 100)
    public void supportedNeighborSurvivesPlayerExtraction(GameTestHelper helper) {
        BarColumnChecks.supportedNeighborSurvivesPlayerExtraction(
                helper, FabricGameTestSupport.playerFactory(helper));
    }

    /** See {@link BarColumnChecks#differentlyOrientedSeamUsesFootprintOverlap}. */
    @GameTest(structure = FabricGameTestSupport.TEMPLATE, maxTicks = 100)
    public void differentlyOrientedSeamUsesFootprintOverlap(GameTestHelper helper) {
        BarColumnChecks.differentlyOrientedSeamUsesFootprintOverlap(helper);
    }

    /** See {@link BarColumnChecks#depositTraceStopsAtLastEmptyBeforeOccupiedBar}. */
    @GameTest(structure = FabricGameTestSupport.TEMPLATE, maxTicks = 100)
    public void depositTraceStopsAtLastEmptyBeforeOccupiedBar(GameTestHelper helper) {
        BarColumnChecks.depositTraceStopsAtLastEmptyBeforeOccupiedBar(helper);
    }

    /** See {@link AutomationChecks#barAutomationBackfillsWithoutDropping}. */
    @GameTest(structure = FabricGameTestSupport.TEMPLATE, maxTicks = 100)
    public void automationBackfillsWithoutDropping(GameTestHelper helper) {
        AutomationChecks.barAutomationBackfillsWithoutDropping(
                helper, FabricGameTestSupport::automation);
    }

    /** See {@link AutomationChecks#barInsertionAnswersForThePositionItIsGiven}. */
    @GameTest(structure = FabricGameTestSupport.TEMPLATE, maxTicks = 100)
    public void storageInsertionAnswersForThePositionItIsGiven(GameTestHelper helper) {
        AutomationChecks.barInsertionAnswersForThePositionItIsGiven(
                helper, FabricGameTestSupport::automation);
    }

    /** See {@link AutomationChecks#barBackfillKeepsAStoredItemNoLongerAccepted}. */
    @GameTest(structure = FabricGameTestSupport.TEMPLATE, maxTicks = 100)
    public void backfillKeepsABarItWouldNoLongerAccept(GameTestHelper helper) {
        AutomationChecks.barBackfillKeepsAStoredItemNoLongerAccepted(
                helper, FabricGameTestSupport::automation);
    }

    /** See {@link AutomationChecks#barSimulationDoesNotMutateExtraction}. */
    @GameTest(structure = FabricGameTestSupport.TEMPLATE, maxTicks = 100)
    public void storageSimulationDoesNotMutateExtraction(GameTestHelper helper) {
        AutomationChecks.barSimulationDoesNotMutateExtraction(
                helper, FabricGameTestSupport::automation);
    }

    /** See {@link BarColumnChecks#breakingLowerBlockCollapsesDependentUpperBlock}. */
    @GameTest(structure = FabricGameTestSupport.TEMPLATE, maxTicks = 100)
    public void breakingLowerBlockCollapsesDependentUpperBlock(GameTestHelper helper) {
        BarColumnChecks.breakingLowerBlockCollapsesDependentUpperBlock(helper);
    }

    /** See {@link BarColumnChecks#breakingFullColumnConsolidatesDrops}. */
    @GameTest(structure = FabricGameTestSupport.TEMPLATE, maxTicks = 100)
    public void breakingFullColumnConsolidatesDrops(GameTestHelper helper) {
        BarColumnChecks.breakingFullColumnConsolidatesDrops(helper);
    }

    /** See {@link BarColumnChecks#playerCascadeConsolidatesAcrossBlocks}. */
    @GameTest(structure = FabricGameTestSupport.TEMPLATE, maxTicks = 100)
    public void playerCascadeConsolidatesAcrossBlocks(GameTestHelper helper) {
        BarColumnChecks.playerCascadeConsolidatesAcrossBlocks(
                helper, FabricGameTestSupport.playerFactory(helper));
    }

    /** See {@link BarColumnChecks#collapseDoesNotMergeDifferentComponents}. */
    @GameTest(structure = FabricGameTestSupport.TEMPLATE, maxTicks = 100)
    public void collapseDoesNotMergeDifferentComponents(GameTestHelper helper) {
        BarColumnChecks.collapseDoesNotMergeDifferentComponents(
                helper, FabricGameTestSupport.playerFactory(helper));
    }

    /** See {@link BarColumnChecks#comparatorReservesZeroForAnEmptyColumn}. */
    @GameTest(structure = FabricGameTestSupport.TEMPLATE, maxTicks = 100)
    public void comparatorReservesZeroForAnEmptyColumn(GameTestHelper helper) {
        BarColumnChecks.comparatorReservesZeroForAnEmptyColumn(helper);
    }

    /** See {@link BarColumnChecks#comparatorReadsTheWholeColumnFromEveryBlock}. */
    @GameTest(structure = FabricGameTestSupport.TEMPLATE, maxTicks = 100)
    public void comparatorReadsTheWholeColumnFromEveryBlock(GameTestHelper helper) {
        BarColumnChecks.comparatorReadsTheWholeColumnFromEveryBlock(helper);
    }

    /** See {@link BarColumnChecks#comparatorFollowsAColumnLosingABlock}. */
    @GameTest(structure = FabricGameTestSupport.TEMPLATE, maxTicks = 100)
    public void comparatorFollowsAColumnLosingABlock(GameTestHelper helper) {
        BarColumnChecks.comparatorFollowsAColumnLosingABlock(helper);
    }

    /** See {@link BarColumnChecks#contentsDropWithBlockDropsDisabled}. */
    @GameTest(structure = FabricGameTestSupport.TEMPLATE, maxTicks = 100)
    public void contentsDropWithBlockDropsDisabled(GameTestHelper helper) {
        BarColumnChecks.contentsDropWithBlockDropsDisabled(helper);
    }
}
