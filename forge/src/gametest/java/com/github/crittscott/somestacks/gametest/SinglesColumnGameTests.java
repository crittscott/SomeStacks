package com.github.crittscott.somestacks.gametest;

import com.github.crittscott.somestacks.SomeStacksCommon;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/**
 * Singles support and gravity in a live level: grounding at the bottom of a column, a cell resting
 * on the one below it, visual columns lining up across differently rotated blocks, and extraction
 * shifting a column down while carrying each item's rotation with it.
 */
@GameTestHolder(SomeStacksCommon.MODID)
@PrefixGameTestTemplate(false)
public final class SinglesColumnGameTests {
    private SinglesColumnGameTests() {}

    /**
     * Loader entry point for {@link
     * SinglesColumnChecks#standaloneBottomIsGroundedAndFloatingCellIsRejected}; see that method for
     * the scenario, manual check, and any internal-only assertions.
     */
    @GameTest(template = ForgeGameTestSupport.TEMPLATE)
    public static void standaloneBottomIsGroundedAndFloatingCellIsRejected(GameTestHelper helper) {
        SinglesColumnChecks.standaloneBottomIsGroundedAndFloatingCellIsRejected(helper);
    }

    /**
     * Loader entry point for {@link SinglesColumnChecks#upperCellUsesTheCellImmediatelyBelow}; see
     * that method for the scenario, manual check, and any internal-only assertions.
     */
    @GameTest(template = ForgeGameTestSupport.TEMPLATE)
    public static void upperCellUsesTheCellImmediatelyBelow(GameTestHelper helper) {
        SinglesColumnChecks.upperCellUsesTheCellImmediatelyBelow(helper);
    }

    /**
     * Loader entry point for {@link
     * SinglesColumnChecks#differentlyRotatedBlocksShareVisualSeamColumns}; see that method for the
     * scenario, manual check, and any internal-only assertions.
     */
    @GameTest(template = ForgeGameTestSupport.TEMPLATE)
    public static void differentlyRotatedBlocksShareVisualSeamColumns(GameTestHelper helper) {
        SinglesColumnChecks.differentlyRotatedBlocksShareVisualSeamColumns(helper);
    }

    /**
     * Loader entry point for {@link
     * SinglesColumnChecks#extractionShiftsOneColumnAndCarriesItemRotation}; see that method for the
     * scenario, manual check, and any internal-only assertions.
     */
    @GameTest(template = ForgeGameTestSupport.TEMPLATE)
    public static void extractionShiftsOneColumnAndCarriesItemRotation(GameTestHelper helper) {
        SinglesColumnChecks.extractionShiftsOneColumnAndCarriesItemRotation(
                helper, ForgeGameTestSupport.players(helper), ForgeGameTestSupport::click);
    }

    /**
     * Loader entry point for {@link SinglesColumnChecks#shiftKeepsAnItemItWouldNoLongerAccept}; see
     * that method for the scenario, manual check, and any internal-only assertions.
     */
    @GameTest(template = ForgeGameTestSupport.TEMPLATE)
    public static void shiftKeepsAnItemItWouldNoLongerAccept(GameTestHelper helper) {
        SinglesColumnChecks.shiftKeepsAnItemItWouldNoLongerAccept(
                helper, ForgeGameTestSupport.players(helper), ForgeGameTestSupport::click);
    }

    /**
     * Loader entry point for {@link SinglesColumnChecks#drawDownKeepsAnItemItWouldNoLongerAccept};
     * see that method for the scenario, manual check, and any internal-only assertions.
     */
    @GameTest(template = ForgeGameTestSupport.TEMPLATE)
    public static void drawDownKeepsAnItemItWouldNoLongerAccept(GameTestHelper helper) {
        SinglesColumnChecks.drawDownKeepsAnItemItWouldNoLongerAccept(
                helper, ForgeGameTestSupport.players(helper), ForgeGameTestSupport::click);
    }

    /**
     * Loader entry point for {@link SinglesColumnChecks#extractionPreservesAnExistingGap}; see that
     * method for the scenario, manual check, and any internal-only assertions.
     */
    @GameTest(template = ForgeGameTestSupport.TEMPLATE)
    public static void extractionPreservesAnExistingGap(GameTestHelper helper) {
        SinglesColumnChecks.extractionPreservesAnExistingGap(
                helper, ForgeGameTestSupport.players(helper), ForgeGameTestSupport::click);
    }

    /**
     * Loader entry point for {@link
     * SinglesColumnChecks#extractionDrawsAcrossDifferentlyRotatedBlockBoundary}; see that method
     * for the scenario, manual check, and any internal-only assertions.
     */
    @GameTest(template = ForgeGameTestSupport.TEMPLATE)
    public static void extractionDrawsAcrossDifferentlyRotatedBlockBoundary(GameTestHelper helper) {
        SinglesColumnChecks.extractionDrawsAcrossDifferentlyRotatedBlockBoundary(
                helper, ForgeGameTestSupport.players(helper), ForgeGameTestSupport::click);
    }

    /**
     * Loader entry point for {@link
     * SinglesColumnChecks#capabilityInsertionAnswersForTheCellItIsGiven}; see that method for the
     * scenario, manual check, and any internal-only assertions.
     */
    @GameTest(template = ForgeGameTestSupport.TEMPLATE)
    public static void capabilityInsertionAnswersForTheCellItIsGiven(GameTestHelper helper) {
        SinglesColumnChecks.capabilityInsertionAnswersForTheCellItIsGiven(
                helper, ForgeGameTestSupport::automation);
    }

    /**
     * Loader entry point for {@link SinglesColumnChecks#capabilityWalkFillsTheColumnFromTheBottom};
     * see that method for the scenario, manual check, and any internal-only assertions.
     */
    @GameTest(template = ForgeGameTestSupport.TEMPLATE)
    public static void capabilityWalkFillsTheColumnFromTheBottom(GameTestHelper helper) {
        SinglesColumnChecks.capabilityWalkFillsTheColumnFromTheBottom(
                helper, ForgeGameTestSupport::automation);
    }

    /**
     * Loader entry point for {@link
     * SinglesColumnChecks#capabilityExtractionUsesThePlayerGravityPath}; see that method for the
     * scenario, manual check, and any internal-only assertions.
     */
    @GameTest(template = ForgeGameTestSupport.TEMPLATE)
    public static void capabilityExtractionUsesThePlayerGravityPath(GameTestHelper helper) {
        SinglesColumnChecks.capabilityExtractionUsesThePlayerGravityPath(
                helper, ForgeGameTestSupport::automation);
    }

    /**
     * Loader entry point for {@link
     * SinglesColumnChecks#depositTraceStopsAtLastEmptyBeforeOccupiedCell}; see that method for the
     * scenario, manual check, and any internal-only assertions.
     */
    @GameTest(template = ForgeGameTestSupport.TEMPLATE)
    public static void depositTraceStopsAtLastEmptyBeforeOccupiedCell(GameTestHelper helper) {
        SinglesColumnChecks.depositTraceStopsAtLastEmptyBeforeOccupiedCell(helper);
    }

    /**
     * Loader entry point for {@link
     * SinglesColumnChecks#depositTraceUsesFarthestCellWhenNothingIsOccupied}; see that method for
     * the scenario, manual check, and any internal-only assertions.
     */
    @GameTest(template = ForgeGameTestSupport.TEMPLATE)
    public static void depositTraceUsesFarthestCellWhenNothingIsOccupied(GameTestHelper helper) {
        SinglesColumnChecks.depositTraceUsesFarthestCellWhenNothingIsOccupied(helper);
    }

    /**
     * Loader entry point for {@link
     * SinglesColumnChecks#comparatorReadsTheWholeColumnFromEveryBlock}; see that method for the
     * scenario, manual check, and any internal-only assertions.
     */
    @GameTest(template = ForgeGameTestSupport.TEMPLATE)
    public static void comparatorReadsTheWholeColumnFromEveryBlock(GameTestHelper helper) {
        SinglesColumnChecks.comparatorReadsTheWholeColumnFromEveryBlock(helper);
    }

    /**
     * Loader entry point for {@link SinglesColumnChecks#comparatorFollowsAColumnLosingABlock}; see
     * that method for the scenario, manual check, and any internal-only assertions.
     */
    @GameTest(template = ForgeGameTestSupport.TEMPLATE)
    public static void comparatorFollowsAColumnLosingABlock(GameTestHelper helper) {
        SinglesColumnChecks.comparatorFollowsAColumnLosingABlock(helper);
    }

    /**
     * See {@link SinglesColumnChecks#middleRemovalPreservesAndPublishesBothSurvivingColumns} for
     * the split scenario and manual procedure.
     */
    @GameTest(template = ForgeGameTestSupport.TEMPLATE)
    public static void middleRemovalPreservesAndPublishesBothSurvivingColumns(
            GameTestHelper helper) {
        SinglesColumnChecks.middleRemovalPreservesAndPublishesBothSurvivingColumns(helper);
    }
}
