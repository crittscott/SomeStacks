package com.github.crittscott.somestacks.gametest;

import com.github.crittscott.somestacks.SomeStacks;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraftforge.gametest.GameTestHolder;

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
@GameTestHolder(SomeStacks.MODID)
public final class SinglesColumnGameTests {
    private SinglesColumnGameTests() {}

    /** See {@link SinglesColumnChecks#standaloneBottomIsGroundedAndFloatingCellIsRejected}. */
    @GameTest(template = GameTestSupport.TEMPLATE)
    public static void standaloneBottomIsGroundedAndFloatingCellIsRejected(GameTestHelper helper) {
        SinglesColumnChecks.standaloneBottomIsGroundedAndFloatingCellIsRejected(helper);
    }

    /** See {@link SinglesColumnChecks#upperCellUsesTheCellImmediatelyBelow}. */
    @GameTest(template = GameTestSupport.TEMPLATE)
    public static void upperCellUsesTheCellImmediatelyBelow(GameTestHelper helper) {
        SinglesColumnChecks.upperCellUsesTheCellImmediatelyBelow(helper);
    }

    /** See {@link SinglesColumnChecks#differentlyRotatedBlocksShareVisualSeamColumns}. */
    @GameTest(template = GameTestSupport.TEMPLATE)
    public static void differentlyRotatedBlocksShareVisualSeamColumns(GameTestHelper helper) {
        SinglesColumnChecks.differentlyRotatedBlocksShareVisualSeamColumns(helper);
    }

    /** See {@link SinglesColumnChecks#extractionShiftsOneColumnAndCarriesItemRotation}. */
    @GameTest(template = GameTestSupport.TEMPLATE)
    public static void extractionShiftsOneColumnAndCarriesItemRotation(GameTestHelper helper) {
        SinglesColumnChecks.extractionShiftsOneColumnAndCarriesItemRotation(helper);
    }

    /** See {@link SinglesColumnChecks#shiftKeepsAnItemItWouldNoLongerAccept}. */
    @GameTest(template = GameTestSupport.TEMPLATE)
    public static void shiftKeepsAnItemItWouldNoLongerAccept(GameTestHelper helper) {
        SinglesColumnChecks.shiftKeepsAnItemItWouldNoLongerAccept(helper);
    }

    /** See {@link SinglesColumnChecks#drawDownKeepsAnItemItWouldNoLongerAccept}. */
    @GameTest(template = GameTestSupport.TEMPLATE)
    public static void drawDownKeepsAnItemItWouldNoLongerAccept(GameTestHelper helper) {
        SinglesColumnChecks.drawDownKeepsAnItemItWouldNoLongerAccept(helper);
    }

    /** See {@link SinglesColumnChecks#extractionPreservesAnExistingGap}. */
    @GameTest(template = GameTestSupport.TEMPLATE)
    public static void extractionPreservesAnExistingGap(GameTestHelper helper) {
        SinglesColumnChecks.extractionPreservesAnExistingGap(helper);
    }

    /** See {@link SinglesColumnChecks#extractionDrawsAcrossDifferentlyRotatedBlockBoundary}. */
    @GameTest(template = GameTestSupport.TEMPLATE)
    public static void extractionDrawsAcrossDifferentlyRotatedBlockBoundary(GameTestHelper helper) {
        SinglesColumnChecks.extractionDrawsAcrossDifferentlyRotatedBlockBoundary(helper);
    }

    /**
     * To reproduce in-game: target individual Singles cells with item automation; only the named
     * supported empty cell accepts the item, without redirecting it elsewhere.
     */
    @GameTest(template = GameTestSupport.TEMPLATE)
    public static void capabilityInsertionAnswersForTheCellItIsGiven(GameTestHelper helper) {
        AutomationChecks.singlesInsertionAnswersForTheCellItIsGiven(helper);
    }

    /**
     * To reproduce in-game: let item automation walk the advertised Singles cells in order and
     * verify the column fills from the lowest supported cell upward.
     */
    @GameTest(template = GameTestSupport.TEMPLATE)
    public static void capabilityWalkFillsTheColumnFromTheBottom(GameTestHelper helper) {
        AutomationChecks.singlesWalkFillsFromTheBottom(helper);
    }

    /**
     * To reproduce in-game: extract a lower Singles cell through item automation and verify the item
     * above shifts down with its rotation, just as it does after player extraction.
     */
    @GameTest(template = GameTestSupport.TEMPLATE)
    public static void capabilityExtractionUsesThePlayerGravityPath(GameTestHelper helper) {
        AutomationChecks.singlesExtractionUsesThePlayerGravityPath(helper);
    }

    /** See {@link SinglesColumnChecks#depositTraceStopsAtLastEmptyBeforeOccupiedCell}. */
    @GameTest(template = GameTestSupport.TEMPLATE)
    public static void depositTraceStopsAtLastEmptyBeforeOccupiedCell(GameTestHelper helper) {
        SinglesColumnChecks.depositTraceStopsAtLastEmptyBeforeOccupiedCell(helper);
    }

    /** See {@link SinglesColumnChecks#depositTraceUsesFarthestCellWhenNothingIsOccupied}. */
    @GameTest(template = GameTestSupport.TEMPLATE)
    public static void depositTraceUsesFarthestCellWhenNothingIsOccupied(GameTestHelper helper) {
        SinglesColumnChecks.depositTraceUsesFarthestCellWhenNothingIsOccupied(helper);
    }

    /** See {@link SinglesColumnChecks#breakingMiddleBlockDropsItsContentsWithoutClosingTheGap}. */
    @GameTest(template = GameTestSupport.TEMPLATE)
    public static void breakingMiddleBlockDropsItsContentsWithoutClosingTheGap(
            GameTestHelper helper) {
        SinglesColumnChecks.breakingMiddleBlockDropsItsContentsWithoutClosingTheGap(helper);
    }

    /** See {@link SinglesColumnChecks#comparatorReservesZeroForAnEmptyColumn}. */
    @GameTest(template = GameTestSupport.TEMPLATE)
    public static void comparatorReservesZeroForAnEmptyColumn(GameTestHelper helper) {
        SinglesColumnChecks.comparatorReservesZeroForAnEmptyColumn(helper);
    }

    /** See {@link SinglesColumnChecks#comparatorReadsTheWholeColumnFromEveryBlock}. */
    @GameTest(template = GameTestSupport.TEMPLATE)
    public static void comparatorReadsTheWholeColumnFromEveryBlock(GameTestHelper helper) {
        SinglesColumnChecks.comparatorReadsTheWholeColumnFromEveryBlock(helper);
    }

    /** See {@link SinglesColumnChecks#comparatorFollowsAColumnLosingABlock}. */
    @GameTest(template = GameTestSupport.TEMPLATE)
    public static void comparatorFollowsAColumnLosingABlock(GameTestHelper helper) {
        SinglesColumnChecks.comparatorFollowsAColumnLosingABlock(helper);
    }
}
