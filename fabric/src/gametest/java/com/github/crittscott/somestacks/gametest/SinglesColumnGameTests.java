package com.github.crittscott.somestacks.gametest;

import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;

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
     * that method for the scenario, manual check, and any internal-only assertions.
     */
    @GameTest(template = FabricGameTestSupport.TEMPLATE)
    public void upperCellUsesTheCellImmediatelyBelow(GameTestHelper helper) {
        SinglesColumnChecks.upperCellUsesTheCellImmediatelyBelow(helper);
    }

    /**
     * Loader entry point for {@link
     * SinglesColumnChecks#differentlyRotatedBlocksShareVisualSeamColumns}; see that method for the
     * scenario, manual check, and any internal-only assertions.
     */
    @GameTest(template = FabricGameTestSupport.TEMPLATE)
    public void differentlyRotatedBlocksShareVisualSeamColumns(GameTestHelper helper) {
        SinglesColumnChecks.differentlyRotatedBlocksShareVisualSeamColumns(helper);
    }

    /**
     * Loader entry point for {@link
     * SinglesColumnChecks#extractionShiftsOneColumnAndCarriesItemRotation}; see that method for the
     * scenario, manual check, and any internal-only assertions.
     */
    @GameTest(template = FabricGameTestSupport.TEMPLATE)
    public void extractionShiftsOneColumnAndCarriesItemRotation(GameTestHelper helper) {
        SinglesColumnChecks.extractionShiftsOneColumnAndCarriesItemRotation(
                helper, FabricGameTestSupport.players(helper), FabricGameTestSupport::click);
    }

    /**
     * Loader entry point for {@link SinglesColumnChecks#shiftKeepsAnItemItWouldNoLongerAccept}; see
     * that method for the scenario, manual check, and any internal-only assertions.
     */
    @GameTest(template = FabricGameTestSupport.TEMPLATE)
    public void shiftKeepsAnItemItWouldNoLongerAccept(GameTestHelper helper) {
        SinglesColumnChecks.shiftKeepsAnItemItWouldNoLongerAccept(
                helper, FabricGameTestSupport.players(helper), FabricGameTestSupport::click);
    }

    /**
     * Loader entry point for {@link SinglesColumnChecks#drawDownKeepsAnItemItWouldNoLongerAccept};
     * see that method for the scenario, manual check, and any internal-only assertions.
     */
    @GameTest(template = FabricGameTestSupport.TEMPLATE)
    public void drawDownKeepsAnItemItWouldNoLongerAccept(GameTestHelper helper) {
        SinglesColumnChecks.drawDownKeepsAnItemItWouldNoLongerAccept(
                helper, FabricGameTestSupport.players(helper), FabricGameTestSupport::click);
    }

    /**
     * Loader entry point for {@link SinglesColumnChecks#extractionPreservesAnExistingGap}; see that
     * method for the scenario, manual check, and any internal-only assertions.
     */
    @GameTest(template = FabricGameTestSupport.TEMPLATE)
    public void extractionPreservesAnExistingGap(GameTestHelper helper) {
        SinglesColumnChecks.extractionPreservesAnExistingGap(
                helper, FabricGameTestSupport.players(helper), FabricGameTestSupport::click);
    }

    /**
     * Loader entry point for {@link
     * SinglesColumnChecks#extractionDrawsAcrossDifferentlyRotatedBlockBoundary}; see that method
     * for the scenario, manual check, and any internal-only assertions.
     */
    @GameTest(template = FabricGameTestSupport.TEMPLATE)
    public void extractionDrawsAcrossDifferentlyRotatedBlockBoundary(GameTestHelper helper) {
        SinglesColumnChecks.extractionDrawsAcrossDifferentlyRotatedBlockBoundary(
                helper, FabricGameTestSupport.players(helper), FabricGameTestSupport::click);
    }

    /**
     * Loader entry point for {@link
     * SinglesColumnChecks#capabilityInsertionAnswersForTheCellItIsGiven}; see that method for the
     * scenario, manual check, and any internal-only assertions.
     */
    @GameTest(template = FabricGameTestSupport.TEMPLATE)
    public void capabilityInsertionAnswersForTheCellItIsGiven(GameTestHelper helper) {
        SinglesColumnChecks.capabilityInsertionAnswersForTheCellItIsGiven(
                helper, FabricGameTestSupport::automation);
    }

    /**
     * Loader entry point for {@link SinglesColumnChecks#capabilityWalkFillsTheColumnFromTheBottom};
     * see that method for the scenario, manual check, and any internal-only assertions.
     */
    @GameTest(template = FabricGameTestSupport.TEMPLATE)
    public void capabilityWalkFillsTheColumnFromTheBottom(GameTestHelper helper) {
        SinglesColumnChecks.capabilityWalkFillsTheColumnFromTheBottom(
                helper, FabricGameTestSupport::automation);
    }

    /**
     * Loader entry point for {@link
     * SinglesColumnChecks#capabilityExtractionUsesThePlayerGravityPath}; see that method for the
     * scenario, manual check, and any internal-only assertions.
     */
    @GameTest(template = FabricGameTestSupport.TEMPLATE)
    public void capabilityExtractionUsesThePlayerGravityPath(GameTestHelper helper) {
        SinglesColumnChecks.capabilityExtractionUsesThePlayerGravityPath(
                helper, FabricGameTestSupport::automation);
    }

    /**
     * Loader entry point for {@link
     * SinglesColumnChecks#depositTraceStopsAtLastEmptyBeforeOccupiedCell}; see that method for the
     * scenario, manual check, and any internal-only assertions.
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
     * Loader entry point for {@link
     * SinglesColumnChecks#comparatorReadsTheWholeColumnFromEveryBlock}; see that method for the
     * scenario, manual check, and any internal-only assertions.
     */
    @GameTest(template = FabricGameTestSupport.TEMPLATE)
    public void comparatorReadsTheWholeColumnFromEveryBlock(GameTestHelper helper) {
        SinglesColumnChecks.comparatorReadsTheWholeColumnFromEveryBlock(helper);
    }

    /**
     * Loader entry point for {@link SinglesColumnChecks#comparatorFollowsAColumnLosingABlock}; see
     * that method for the scenario, manual check, and any internal-only assertions.
     */
    @GameTest(template = FabricGameTestSupport.TEMPLATE)
    public void comparatorFollowsAColumnLosingABlock(GameTestHelper helper) {
        SinglesColumnChecks.comparatorFollowsAColumnLosingABlock(helper);
    }

    /**
     * See {@link SinglesColumnChecks#middleRemovalPreservesAndPublishesBothSurvivingColumns} for
     * the split scenario and manual procedure.
     */
    @GameTest(template = FabricGameTestSupport.TEMPLATE)
    public void middleRemovalPreservesAndPublishesBothSurvivingColumns(GameTestHelper helper) {
        SinglesColumnChecks.middleRemovalPreservesAndPublishesBothSurvivingColumns(helper);
    }
}
