package com.github.crittscott.somestacks.gametest;

import net.minecraft.gametest.framework.GameTestHelper;

/**
 * Bar support and collapse in a live level: item validity, grounding at the bottom of a column,
 * footprint overlap deciding support across the alternating layer orientations, and the difference
 * between player extraction, which drops what it unsupports, and automated extraction, which
 * backfills from the top instead.
 */
public final class BarColumnGameTests {
    public BarColumnGameTests() {}

    /** See {@link BarColumnChecks#validityAndBottomGroundingAreEnforced}. */
    public static void validityAndBottomGroundingAreEnforced(GameTestHelper helper) {
        BarColumnChecks.validityAndBottomGroundingAreEnforced(helper);
    }

    /** See {@link BarColumnChecks#bricksAreBarItemsButBrickBlocksAreNot}. */
    public static void bricksAreBarItemsButBrickBlocksAreNot(GameTestHelper helper) {
        BarColumnChecks.bricksAreBarItemsButBrickBlocksAreNot(helper);
    }

    /** See {@link BarColumnChecks#upperBarRequiresOverlappingSupport}. */
    public static void upperBarRequiresOverlappingSupport(GameTestHelper helper) {
        BarColumnChecks.upperBarRequiresOverlappingSupport(helper);
    }

    /** See {@link BarColumnChecks#playerExtractionDropsUnsupportedBars}. */
    public static void playerExtractionDropsUnsupportedBars(GameTestHelper helper) {
        BarColumnChecks.playerExtractionDropsUnsupportedBars(
                helper, GameTestSupport.playerFactory(helper));
    }

    /** See {@link BarColumnChecks#supportedNeighborSurvivesPlayerExtraction}. */
    public static void supportedNeighborSurvivesPlayerExtraction(GameTestHelper helper) {
        BarColumnChecks.supportedNeighborSurvivesPlayerExtraction(
                helper, GameTestSupport.playerFactory(helper));
    }

    /** See {@link BarColumnChecks#differentlyOrientedSeamUsesFootprintOverlap}. */
    public static void differentlyOrientedSeamUsesFootprintOverlap(GameTestHelper helper) {
        BarColumnChecks.differentlyOrientedSeamUsesFootprintOverlap(helper);
    }

    /** See {@link BarColumnChecks#depositTraceStopsAtLastEmptyBeforeOccupiedBar}. */
    public static void depositTraceStopsAtLastEmptyBeforeOccupiedBar(GameTestHelper helper) {
        BarColumnChecks.depositTraceStopsAtLastEmptyBeforeOccupiedBar(helper);
    }

    /** See {@link AutomationChecks#barAutomationBackfillsWithoutDropping}. */
    public static void automationBackfillsWithoutDropping(GameTestHelper helper) {
        AutomationChecks.barAutomationBackfillsWithoutDropping(helper, GameTestSupport::automation);
    }

    /** See {@link AutomationChecks#barInsertionAnswersForThePositionItIsGiven}. */
    public static void capabilityInsertionAnswersForThePositionItIsGiven(GameTestHelper helper) {
        AutomationChecks.barInsertionAnswersForThePositionItIsGiven(
                helper, GameTestSupport::automation);
    }

    /** See {@link AutomationChecks#barBackfillKeepsAStoredItemNoLongerAccepted}. */
    public static void backfillKeepsABarItWouldNoLongerAccept(GameTestHelper helper) {
        AutomationChecks.barBackfillKeepsAStoredItemNoLongerAccepted(
                helper, GameTestSupport::automation);
    }

    /** See {@link AutomationChecks#barSimulationDoesNotMutateExtraction}. */
    public static void capabilitySimulationDoesNotMutateExtraction(GameTestHelper helper) {
        AutomationChecks.barSimulationDoesNotMutateExtraction(helper, GameTestSupport::automation);
    }

    /** See {@link BarColumnChecks#breakingLowerBlockCollapsesDependentUpperBlock}. */
    public static void breakingLowerBlockCollapsesDependentUpperBlock(GameTestHelper helper) {
        BarColumnChecks.breakingLowerBlockCollapsesDependentUpperBlock(helper);
    }

    /** See {@link BarColumnChecks#breakingFullColumnConsolidatesDrops}. */
    public static void breakingFullColumnConsolidatesDrops(GameTestHelper helper) {
        BarColumnChecks.breakingFullColumnConsolidatesDrops(helper);
    }

    /** See {@link BarColumnChecks#playerCascadeConsolidatesAcrossBlocks}. */
    public static void playerCascadeConsolidatesAcrossBlocks(GameTestHelper helper) {
        BarColumnChecks.playerCascadeConsolidatesAcrossBlocks(
                helper, GameTestSupport.playerFactory(helper));
    }

    /** See {@link BarColumnChecks#collapseDoesNotMergeDifferentComponents}. */
    public static void collapseDoesNotMergeDifferentComponents(GameTestHelper helper) {
        BarColumnChecks.collapseDoesNotMergeDifferentComponents(
                helper, GameTestSupport.playerFactory(helper));
    }

    /** See {@link BarColumnChecks#comparatorReservesZeroForAnEmptyColumn}. */
    public static void comparatorReservesZeroForAnEmptyColumn(GameTestHelper helper) {
        BarColumnChecks.comparatorReservesZeroForAnEmptyColumn(helper);
    }

    /** See {@link BarColumnChecks#comparatorReadsTheWholeColumnFromEveryBlock}. */
    public static void comparatorReadsTheWholeColumnFromEveryBlock(GameTestHelper helper) {
        BarColumnChecks.comparatorReadsTheWholeColumnFromEveryBlock(helper);
    }

    /** See {@link BarColumnChecks#comparatorFollowsAColumnLosingABlock}. */
    public static void comparatorFollowsAColumnLosingABlock(GameTestHelper helper) {
        BarColumnChecks.comparatorFollowsAColumnLosingABlock(helper);
    }

    /** See {@link BarColumnChecks#contentsDropWithBlockDropsDisabled}. */
    public static void contentsDropWithBlockDropsDisabled(GameTestHelper helper) {
        BarColumnChecks.contentsDropWithBlockDropsDisabled(helper);
    }
}
