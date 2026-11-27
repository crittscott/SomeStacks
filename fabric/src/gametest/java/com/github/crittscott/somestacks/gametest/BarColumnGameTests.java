package com.github.crittscott.somestacks.gametest;

import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;

/**
 * Bar support and collapse in a live level: item validity, grounding at the bottom of a column,
 * footprint overlap deciding support across the alternating layer orientations, and the difference
 * between player extraction, which drops what it unsupports, and automated extraction, which
 * backfills from the top instead.
 */
public final class BarColumnGameTests implements FabricGameTest {
    /**
     * Loader entry point for {@link BarColumnChecks#validityAndBottomGroundingAreEnforced}; see
     * that method for the scenario, manual check, and any internal-only assertions.
     */
    @GameTest(template = FabricGameTestSupport.TEMPLATE)
    public void validityAndBottomGroundingAreEnforced(GameTestHelper helper) {
        BarColumnChecks.validityAndBottomGroundingAreEnforced(helper);
    }

    /** See {@link BarColumnChecks#barAdmissionFollowsTheIngotTag}. */
    @GameTest(template = FabricGameTestSupport.TEMPLATE)
    public void barAdmissionFollowsTheIngotTag(GameTestHelper helper) {
        BarColumnChecks.barAdmissionFollowsTheIngotTag(helper);
    }

    /**
     * Loader entry point for {@link BarColumnChecks#upperBarRequiresOverlappingSupport}; see that
     * method for the scenario, manual check, and any internal-only assertions.
     */
    @GameTest(template = FabricGameTestSupport.TEMPLATE)
    public void upperBarRequiresOverlappingSupport(GameTestHelper helper) {
        BarColumnChecks.upperBarRequiresOverlappingSupport(helper);
    }

    /**
     * Loader entry point for {@link BarColumnChecks#playerExtractionDropsUnsupportedBars}; see that
     * method for the scenario, manual check, and any internal-only assertions.
     */
    @GameTest(template = FabricGameTestSupport.TEMPLATE)
    public void playerExtractionDropsUnsupportedBars(GameTestHelper helper) {
        BarColumnChecks.playerExtractionDropsUnsupportedBars(
                helper, FabricGameTestSupport.players(helper), FabricGameTestSupport::click);
    }

    /**
     * Loader entry point for {@link BarColumnChecks#supportedNeighborSurvivesPlayerExtraction}; see
     * that method for the scenario, manual check, and any internal-only assertions.
     */
    @GameTest(template = FabricGameTestSupport.TEMPLATE)
    public void supportedNeighborSurvivesPlayerExtraction(GameTestHelper helper) {
        BarColumnChecks.supportedNeighborSurvivesPlayerExtraction(
                helper, FabricGameTestSupport.players(helper), FabricGameTestSupport::click);
    }

    /**
     * Loader entry point for {@link BarColumnChecks#differentlyOrientedSeamUsesFootprintOverlap};
     * see that method for the scenario, manual check, and any internal-only assertions.
     */
    @GameTest(template = FabricGameTestSupport.TEMPLATE)
    public void differentlyOrientedSeamUsesFootprintOverlap(GameTestHelper helper) {
        BarColumnChecks.differentlyOrientedSeamUsesFootprintOverlap(helper);
    }

    /**
     * Loader entry point for {@link BarColumnChecks#automationBackfillsWithoutDropping}; see that
     * method for the scenario, manual check, and any internal-only assertions.
     */
    @GameTest(template = FabricGameTestSupport.TEMPLATE)
    public void automationBackfillsWithoutDropping(GameTestHelper helper) {
        BarColumnChecks.automationBackfillsWithoutDropping(
                helper, FabricGameTestSupport::automation);
    }

    /**
     * Loader entry point for {@link
     * BarColumnChecks#capabilityInsertionAnswersForThePositionItIsGiven}; see that method for the
     * scenario, manual check, and any internal-only assertions.
     */
    @GameTest(template = FabricGameTestSupport.TEMPLATE)
    public void capabilityInsertionAnswersForThePositionItIsGiven(GameTestHelper helper) {
        BarColumnChecks.capabilityInsertionAnswersForThePositionItIsGiven(
                helper, FabricGameTestSupport::automation);
    }

    /**
     * Loader entry point for {@link BarColumnChecks#backfillKeepsABarItWouldNoLongerAccept}; see
     * that method for the scenario, manual check, and any internal-only assertions.
     */
    @GameTest(template = FabricGameTestSupport.TEMPLATE)
    public void backfillKeepsABarItWouldNoLongerAccept(GameTestHelper helper) {
        BarColumnChecks.backfillKeepsABarItWouldNoLongerAccept(
                helper, FabricGameTestSupport::automation);
    }

    /**
     * Loader entry point for {@link BarColumnChecks#capabilitySimulationDoesNotMutateExtraction};
     * see that method for the scenario, manual check, and any internal-only assertions.
     */
    @GameTest(template = FabricGameTestSupport.TEMPLATE)
    public void capabilitySimulationDoesNotMutateExtraction(GameTestHelper helper) {
        BarColumnChecks.capabilitySimulationDoesNotMutateExtraction(
                helper, FabricGameTestSupport::automation);
    }

    /**
     * Loader entry point for {@link
     * BarColumnChecks#breakingLowerBlockCollapsesDependentUpperBlock}; see that method for the
     * scenario, manual check, and any internal-only assertions.
     */
    @GameTest(template = FabricGameTestSupport.TEMPLATE)
    public void breakingLowerBlockCollapsesDependentUpperBlock(GameTestHelper helper) {
        BarColumnChecks.breakingLowerBlockCollapsesDependentUpperBlock(helper);
    }

    /**
     * Loader entry point for {@link BarColumnChecks#breakingFullColumnConsolidatesDrops}; see that
     * method for the scenario, manual check, and any internal-only assertions.
     */
    @GameTest(template = FabricGameTestSupport.TEMPLATE)
    public void breakingFullColumnConsolidatesDrops(GameTestHelper helper) {
        BarColumnChecks.breakingFullColumnConsolidatesDrops(helper);
    }

    /**
     * Loader entry point for {@link BarColumnChecks#breakingFullColumnDropsWithBlockDropsDisabled};
     * see that method for the scenario and manual check.
     */
    @GameTest(template = FabricGameTestSupport.TEMPLATE)
    public void breakingFullColumnDropsWithBlockDropsDisabled(GameTestHelper helper) {
        BarColumnChecks.breakingFullColumnDropsWithBlockDropsDisabled(helper);
    }

    /**
     * Loader entry point for {@link BarColumnChecks#playerCascadeDropsWithBlockDropsDisabled}; see
     * that method for the scenario and manual check.
     */
    @GameTest(template = FabricGameTestSupport.TEMPLATE)
    public void playerCascadeDropsWithBlockDropsDisabled(GameTestHelper helper) {
        BarColumnChecks.playerCascadeDropsWithBlockDropsDisabled(
                helper, FabricGameTestSupport.players(helper), FabricGameTestSupport::click);
    }

    /**
     * Loader entry point for {@link BarColumnChecks#playerCascadeConsolidatesAcrossBlocks}; see
     * that method for the scenario, manual check, and any internal-only assertions.
     */
    @GameTest(template = FabricGameTestSupport.TEMPLATE)
    public void playerCascadeConsolidatesAcrossBlocks(GameTestHelper helper) {
        BarColumnChecks.playerCascadeConsolidatesAcrossBlocks(
                helper, FabricGameTestSupport.players(helper), FabricGameTestSupport::click);
    }

    /**
     * Loader entry point for {@link BarColumnChecks#collapseDoesNotMergeDifferentTags}; see that
     * method for the scenario, manual check, and any internal-only assertions.
     */
    @GameTest(template = FabricGameTestSupport.TEMPLATE)
    public void collapseDoesNotMergeDifferentTags(GameTestHelper helper) {
        BarColumnChecks.collapseDoesNotMergeDifferentTags(
                helper, FabricGameTestSupport.players(helper), FabricGameTestSupport::click);
    }

    /**
     * Loader entry point for {@link BarColumnChecks#comparatorReadsTheWholeColumnFromEveryBlock};
     * see that method for the scenario, manual check, and any internal-only assertions.
     */
    @GameTest(template = FabricGameTestSupport.TEMPLATE)
    public void comparatorReadsTheWholeColumnFromEveryBlock(GameTestHelper helper) {
        BarColumnChecks.comparatorReadsTheWholeColumnFromEveryBlock(helper);
    }

    /**
     * Loader entry point for {@link BarColumnChecks#comparatorFollowsAColumnLosingABlock}; see that
     * method for the scenario, manual check, and any internal-only assertions.
     */
    @GameTest(template = FabricGameTestSupport.TEMPLATE)
    public void comparatorFollowsAColumnLosingABlock(GameTestHelper helper) {
        BarColumnChecks.comparatorFollowsAColumnLosingABlock(helper);
    }
}
