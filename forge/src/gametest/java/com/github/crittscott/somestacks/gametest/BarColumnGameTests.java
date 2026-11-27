package com.github.crittscott.somestacks.gametest;

import com.github.crittscott.somestacks.SomeStacksCommon;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraftforge.gametest.GameTestHolder;

/**
 * Bar support and collapse in a live level: item validity, grounding at the bottom of a column,
 * footprint overlap deciding support across the alternating layer orientations, and the difference
 * between player extraction, which drops what it unsupports, and automated extraction, which
 * backfills from the top instead.
 */
@GameTestHolder(SomeStacksCommon.MODID)
public final class BarColumnGameTests {
    private BarColumnGameTests() {}

    /** See {@link BarColumnChecks#validityAndBottomGroundingAreEnforced}. */
    @GameTest(template = GameTestSupport.TEMPLATE)
    public static void validityAndBottomGroundingAreEnforced(GameTestHelper helper) {
        BarColumnChecks.validityAndBottomGroundingAreEnforced(helper);
    }

    /** See {@link BarColumnChecks#bricksAreBarItemsButBrickBlocksAreNot}. */
    @GameTest(template = GameTestSupport.TEMPLATE)
    public static void bricksAreBarItemsButBrickBlocksAreNot(GameTestHelper helper) {
        BarColumnChecks.bricksAreBarItemsButBrickBlocksAreNot(helper);
    }

    /** See {@link BarColumnChecks#upperBarRequiresOverlappingSupport}. */
    @GameTest(template = GameTestSupport.TEMPLATE)
    public static void upperBarRequiresOverlappingSupport(GameTestHelper helper) {
        BarColumnChecks.upperBarRequiresOverlappingSupport(helper);
    }

    /** See {@link BarColumnChecks#playerExtractionDropsUnsupportedBars}. */
    @GameTest(template = GameTestSupport.TEMPLATE)
    public static void playerExtractionDropsUnsupportedBars(GameTestHelper helper) {
        BarColumnChecks.playerExtractionDropsUnsupportedBars(
                helper, GameTestSupport.playerFactory(helper));
    }

    /** See {@link BarColumnChecks#supportedNeighborSurvivesPlayerExtraction}. */
    @GameTest(template = GameTestSupport.TEMPLATE)
    public static void supportedNeighborSurvivesPlayerExtraction(GameTestHelper helper) {
        BarColumnChecks.supportedNeighborSurvivesPlayerExtraction(
                helper, GameTestSupport.playerFactory(helper));
    }

    /** See {@link BarColumnChecks#differentlyOrientedSeamUsesFootprintOverlap}. */
    @GameTest(template = GameTestSupport.TEMPLATE)
    public static void differentlyOrientedSeamUsesFootprintOverlap(GameTestHelper helper) {
        BarColumnChecks.differentlyOrientedSeamUsesFootprintOverlap(helper);
    }

    /** See {@link BarColumnChecks#depositTraceStopsAtLastEmptyBeforeOccupiedBar}. */
    @GameTest(template = GameTestSupport.TEMPLATE)
    public static void depositTraceStopsAtLastEmptyBeforeOccupiedBar(GameTestHelper helper) {
        BarColumnChecks.depositTraceStopsAtLastEmptyBeforeOccupiedBar(helper);
    }

    /** See {@link AutomationChecks#barAutomationBackfillsWithoutDropping}. */
    @GameTest(template = GameTestSupport.TEMPLATE)
    public static void automationBackfillsWithoutDropping(GameTestHelper helper) {
        AutomationChecks.barAutomationBackfillsWithoutDropping(helper, GameTestSupport::automation);
    }

    /** See {@link AutomationChecks#barInsertionAnswersForThePositionItIsGiven}. */
    @GameTest(template = GameTestSupport.TEMPLATE)
    public static void capabilityInsertionAnswersForThePositionItIsGiven(GameTestHelper helper) {
        AutomationChecks.barInsertionAnswersForThePositionItIsGiven(
                helper, GameTestSupport::automation);
    }

    /** See {@link AutomationChecks#barBackfillKeepsAStoredItemNoLongerAccepted}. */
    @GameTest(template = GameTestSupport.TEMPLATE)
    public static void backfillKeepsABarItWouldNoLongerAccept(GameTestHelper helper) {
        AutomationChecks.barBackfillKeepsAStoredItemNoLongerAccepted(
                helper, GameTestSupport::automation);
    }

    /** See {@link AutomationChecks#barSimulationDoesNotMutateExtraction}. */
    @GameTest(template = GameTestSupport.TEMPLATE)
    public static void capabilitySimulationDoesNotMutateExtraction(GameTestHelper helper) {
        AutomationChecks.barSimulationDoesNotMutateExtraction(helper, GameTestSupport::automation);
    }

    /** See {@link BarColumnChecks#breakingLowerBlockCollapsesDependentUpperBlock}. */
    @GameTest(template = GameTestSupport.TEMPLATE)
    public static void breakingLowerBlockCollapsesDependentUpperBlock(GameTestHelper helper) {
        BarColumnChecks.breakingLowerBlockCollapsesDependentUpperBlock(helper);
    }

    /** See {@link BarColumnChecks#breakingFullColumnConsolidatesDrops}. */
    @GameTest(template = GameTestSupport.TEMPLATE)
    public static void breakingFullColumnConsolidatesDrops(GameTestHelper helper) {
        BarColumnChecks.breakingFullColumnConsolidatesDrops(helper);
    }

    /** See {@link BarColumnChecks#playerCascadeConsolidatesAcrossBlocks}. */
    @GameTest(template = GameTestSupport.TEMPLATE)
    public static void playerCascadeConsolidatesAcrossBlocks(GameTestHelper helper) {
        BarColumnChecks.playerCascadeConsolidatesAcrossBlocks(
                helper, GameTestSupport.playerFactory(helper));
    }

    /** See {@link BarColumnChecks#collapseDoesNotMergeDifferentComponents}. */
    @GameTest(template = GameTestSupport.TEMPLATE)
    public static void collapseDoesNotMergeDifferentComponents(GameTestHelper helper) {
        BarColumnChecks.collapseDoesNotMergeDifferentComponents(
                helper, GameTestSupport.playerFactory(helper));
    }

    /** See {@link BarColumnChecks#comparatorReservesZeroForAnEmptyColumn}. */
    @GameTest(template = GameTestSupport.TEMPLATE)
    public static void comparatorReservesZeroForAnEmptyColumn(GameTestHelper helper) {
        BarColumnChecks.comparatorReservesZeroForAnEmptyColumn(helper);
    }

    /** See {@link BarColumnChecks#comparatorReadsTheWholeColumnFromEveryBlock}. */
    @GameTest(template = GameTestSupport.TEMPLATE)
    public static void comparatorReadsTheWholeColumnFromEveryBlock(GameTestHelper helper) {
        BarColumnChecks.comparatorReadsTheWholeColumnFromEveryBlock(helper);
    }

    /** See {@link BarColumnChecks#comparatorFollowsAColumnLosingABlock}. */
    @GameTest(template = GameTestSupport.TEMPLATE)
    public static void comparatorFollowsAColumnLosingABlock(GameTestHelper helper) {
        BarColumnChecks.comparatorFollowsAColumnLosingABlock(helper);
    }
}
