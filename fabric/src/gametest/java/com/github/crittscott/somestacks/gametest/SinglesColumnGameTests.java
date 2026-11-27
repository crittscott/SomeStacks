package com.github.crittscott.somestacks.gametest;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;

/**
 * Singles support and gravity in a live level: grounding at the bottom of a column, a cell resting
 * on the one below it, visual columns lining up across differently rotated blocks, and extraction
 * shifting a column down while carrying each item's rotation with it.
 */
public final class SinglesColumnGameTests {
    /** See {@link SinglesColumnChecks#standaloneBottomIsGroundedAndFloatingCellIsRejected}. */
    @GameTest(structure = FabricGameTestSupport.TEMPLATE, maxTicks = 100)
    public void standaloneBottomIsGroundedAndFloatingCellIsRejected(GameTestHelper helper) {
        SinglesColumnChecks.standaloneBottomIsGroundedAndFloatingCellIsRejected(helper);
    }

    /** See {@link SinglesColumnChecks#upperCellUsesTheCellImmediatelyBelow}. */
    @GameTest(structure = FabricGameTestSupport.TEMPLATE, maxTicks = 100)
    public void upperCellUsesTheCellImmediatelyBelow(GameTestHelper helper) {
        SinglesColumnChecks.upperCellUsesTheCellImmediatelyBelow(helper);
    }

    /** See {@link SinglesColumnChecks#differentlyRotatedBlocksShareVisualSeamColumns}. */
    @GameTest(structure = FabricGameTestSupport.TEMPLATE, maxTicks = 100)
    public void differentlyRotatedBlocksShareVisualSeamColumns(GameTestHelper helper) {
        SinglesColumnChecks.differentlyRotatedBlocksShareVisualSeamColumns(helper);
    }

    /** See {@link SinglesColumnChecks#extractionShiftsOneColumnAndCarriesItemRotation}. */
    @GameTest(structure = FabricGameTestSupport.TEMPLATE, maxTicks = 100)
    public void extractionShiftsOneColumnAndCarriesItemRotation(GameTestHelper helper) {
        SinglesColumnChecks.extractionShiftsOneColumnAndCarriesItemRotation(
                helper, FabricGameTestSupport.playerFactory(helper));
    }

    /** See {@link SinglesColumnChecks#shiftKeepsAnItemItWouldNoLongerAccept}. */
    @GameTest(structure = FabricGameTestSupport.TEMPLATE, maxTicks = 100)
    public void shiftKeepsAnItemItWouldNoLongerAccept(GameTestHelper helper) {
        SinglesColumnChecks.shiftKeepsAnItemItWouldNoLongerAccept(
                helper, FabricGameTestSupport.playerFactory(helper));
    }

    /** See {@link SinglesColumnChecks#drawDownKeepsAnItemItWouldNoLongerAccept}. */
    @GameTest(structure = FabricGameTestSupport.TEMPLATE, maxTicks = 100)
    public void drawDownKeepsAnItemItWouldNoLongerAccept(GameTestHelper helper) {
        SinglesColumnChecks.drawDownKeepsAnItemItWouldNoLongerAccept(
                helper, FabricGameTestSupport.playerFactory(helper));
    }

    /** See {@link SinglesColumnChecks#extractionPreservesAnExistingGap}. */
    @GameTest(structure = FabricGameTestSupport.TEMPLATE, maxTicks = 100)
    public void extractionPreservesAnExistingGap(GameTestHelper helper) {
        SinglesColumnChecks.extractionPreservesAnExistingGap(
                helper, FabricGameTestSupport.playerFactory(helper));
    }

    /** See {@link SinglesColumnChecks#extractionDrawsAcrossDifferentlyRotatedBlockBoundary}. */
    @GameTest(structure = FabricGameTestSupport.TEMPLATE, maxTicks = 100)
    public void extractionDrawsAcrossDifferentlyRotatedBlockBoundary(GameTestHelper helper) {
        SinglesColumnChecks.extractionDrawsAcrossDifferentlyRotatedBlockBoundary(
                helper, FabricGameTestSupport.playerFactory(helper));
    }

    /** See {@link AutomationChecks#singlesInsertionAnswersForTheCellItIsGiven}. */
    @GameTest(structure = FabricGameTestSupport.TEMPLATE, maxTicks = 100)
    public void storageInsertionAnswersForTheCellItIsGiven(GameTestHelper helper) {
        AutomationChecks.singlesInsertionAnswersForTheCellItIsGiven(
                helper, FabricGameTestSupport::automation);
    }

    /** See {@link AutomationChecks#singlesWalkFillsFromTheBottom}. */
    @GameTest(structure = FabricGameTestSupport.TEMPLATE, maxTicks = 100)
    public void storageWalkFillsTheColumnFromTheBottom(GameTestHelper helper) {
        AutomationChecks.singlesWalkFillsFromTheBottom(helper, FabricGameTestSupport::automation);
    }

    /** See {@link AutomationChecks#singlesExtractionUsesThePlayerGravityPath}. */
    @GameTest(structure = FabricGameTestSupport.TEMPLATE, maxTicks = 100)
    public void storageExtractionUsesThePlayerGravityPath(GameTestHelper helper) {
        AutomationChecks.singlesExtractionUsesThePlayerGravityPath(
                helper, FabricGameTestSupport::automation);
    }

    /** See {@link SinglesColumnChecks#depositTraceStopsAtLastEmptyBeforeOccupiedCell}. */
    @GameTest(structure = FabricGameTestSupport.TEMPLATE, maxTicks = 100)
    public void depositTraceStopsAtLastEmptyBeforeOccupiedCell(GameTestHelper helper) {
        SinglesColumnChecks.depositTraceStopsAtLastEmptyBeforeOccupiedCell(helper);
    }

    /** See {@link SinglesColumnChecks#depositTraceUsesFarthestCellWhenNothingIsOccupied}. */
    @GameTest(structure = FabricGameTestSupport.TEMPLATE, maxTicks = 100)
    public void depositTraceUsesFarthestCellWhenNothingIsOccupied(GameTestHelper helper) {
        SinglesColumnChecks.depositTraceUsesFarthestCellWhenNothingIsOccupied(helper);
    }

    /** See {@link SinglesColumnChecks#breakingMiddleBlockDropsItsContentsWithoutClosingTheGap}. */
    @GameTest(structure = FabricGameTestSupport.TEMPLATE, maxTicks = 100)
    public void breakingMiddleBlockDropsItsContentsWithoutClosingTheGap(GameTestHelper helper) {
        SinglesColumnChecks.breakingMiddleBlockDropsItsContentsWithoutClosingTheGap(helper);
    }

    /** See {@link SinglesColumnChecks#comparatorReservesZeroForAnEmptyColumn}. */
    @GameTest(structure = FabricGameTestSupport.TEMPLATE, maxTicks = 100)
    public void comparatorReservesZeroForAnEmptyColumn(GameTestHelper helper) {
        SinglesColumnChecks.comparatorReservesZeroForAnEmptyColumn(helper);
    }

    /** See {@link SinglesColumnChecks#comparatorReadsTheWholeColumnFromEveryBlock}. */
    @GameTest(structure = FabricGameTestSupport.TEMPLATE, maxTicks = 100)
    public void comparatorReadsTheWholeColumnFromEveryBlock(GameTestHelper helper) {
        SinglesColumnChecks.comparatorReadsTheWholeColumnFromEveryBlock(helper);
    }

    /** See {@link SinglesColumnChecks#comparatorFollowsAColumnLosingABlock}. */
    @GameTest(structure = FabricGameTestSupport.TEMPLATE, maxTicks = 100)
    public void comparatorFollowsAColumnLosingABlock(GameTestHelper helper) {
        SinglesColumnChecks.comparatorFollowsAColumnLosingABlock(helper);
    }
}
