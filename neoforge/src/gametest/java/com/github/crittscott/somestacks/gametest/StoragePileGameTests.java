package com.github.crittscott.somestacks.gametest;

import com.github.crittscott.somestacks.SomeStacksNeoForge;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Storage pile behavior in a live level: deposits filling partial stacks before empty ones, growth
 * when a pile fills, capability insertion addressing the specified slot, obstruction refusing
 * growth, and settling consolidating, packing, and sorting.
 */
@GameTestHolder(SomeStacksNeoForge.MODID)
@PrefixGameTestTemplate(false)
public final class StoragePileGameTests {
    private StoragePileGameTests() {}

    /** See {@link StoragePileChecks#depositFillsCompatiblePartialBeforeEmptySlot}. */
    @GameTest(template = GameTestSupport.TEMPLATE)
    public static void depositFillsCompatiblePartialBeforeEmptySlot(GameTestHelper helper) {
        StoragePileChecks.depositFillsCompatiblePartialBeforeEmptySlot(helper);
    }

    /** See {@link StoragePileChecks#fullPileGrowsByOneBlock}. */
    @GameTest(template = GameTestSupport.TEMPLATE)
    public static void fullPileGrowsByOneBlock(GameTestHelper helper) {
        StoragePileChecks.fullPileGrowsByOneBlock(helper, GameTestSupport.playerFactory(helper));
    }

    /** See {@link AutomationChecks#storageInsertionAnswersForTheSlotItIsGiven}. */
    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = 20)
    public static void capabilityInsertionAnswersForTheSlotItIsGiven(GameTestHelper helper) {
        AutomationChecks.storageInsertionAnswersForTheSlotItIsGiven(
                helper, GameTestSupport::automation);
    }

    /** See {@link AutomationChecks#storageObstructionPreventsGrowthAndSimulationReportsNoRoom}. */
    @GameTest(template = GameTestSupport.TEMPLATE)
    public static void obstructionPreventsGrowthAndSimulationReportsNoRoom(GameTestHelper helper) {
        AutomationChecks.storageObstructionPreventsGrowthAndSimulationReportsNoRoom(
                helper, GameTestSupport::automation);
    }

    /** See {@link StoragePileChecks#settleConsolidatesExactIdentityAndPacksDown}. */
    @GameTest(template = GameTestSupport.TEMPLATE)
    public static void settleConsolidatesExactIdentityAndPacksDown(GameTestHelper helper) {
        StoragePileChecks.settleConsolidatesExactIdentityAndPacksDown(helper);
    }

    /** See {@link StoragePileChecks#settleWritesOnlyChangedCellsWithFreshStacks}. */
    @GameTest(template = GameTestSupport.TEMPLATE)
    public static void settleWritesOnlyChangedCellsWithFreshStacks(GameTestHelper helper) {
        StoragePileChecks.settleWritesOnlyChangedCellsWithFreshStacks(helper);
    }

    /** See {@link StoragePileChecks#storedLightIsPerOccupiedSlotAndCapped}. */
    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = 20)
    public static void storedLightIsPerOccupiedSlotAndCapped(GameTestHelper helper) {
        StoragePileChecks.storedLightIsPerOccupiedSlotAndCapped(helper);
    }

    /** See {@link StoragePileChecks#extractionSettlesOnScheduledBlockTick}. */
    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = 20)
    public static void extractionSettlesOnScheduledBlockTick(GameTestHelper helper) {
        StoragePileChecks.extractionSettlesOnScheduledBlockTick(
                helper, GameTestSupport.playerFactory(helper));
    }

    /** See {@link StoragePileChecks#emptyTemporaryPileDisappearsButPermanentPileRemains}. */
    @GameTest(template = GameTestSupport.TEMPLATE)
    public static void emptyTemporaryPileDisappearsButPermanentPileRemains(GameTestHelper helper) {
        StoragePileChecks.emptyTemporaryPileDisappearsButPermanentPileRemains(helper);
    }

    /** See {@link AutomationChecks#storageFromEveryBlockAddressesTheSamePile}. */
    @GameTest(template = GameTestSupport.TEMPLATE)
    public static void capabilityFromEveryBlockAddressesTheSamePile(GameTestHelper helper) {
        AutomationChecks.storageFromEveryBlockAddressesTheSamePile(
                helper, GameTestSupport::automation);
    }

    /** See {@link StoragePileChecks#comparatorReadsTheWholePileFromEveryBlock}. */
    @GameTest(template = GameTestSupport.TEMPLATE)
    public static void comparatorReadsTheWholePileFromEveryBlock(GameTestHelper helper) {
        StoragePileChecks.comparatorReadsTheWholePileFromEveryBlock(helper);
    }

    /** See {@link StoragePileChecks#ordinaryPlacementBelowPermanentPileRepairsDerivedState}. */
    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = 20)
    public static void ordinaryPlacementBelowPermanentPileRepairsDerivedState(
            GameTestHelper helper) {
        StoragePileChecks.ordinaryPlacementBelowPermanentPileRepairsDerivedState(helper);
    }

    /** See {@link StoragePileChecks#breakingMiddleBlockDropsItsContentsAndSplitsThePile}. */
    @GameTest(template = GameTestSupport.TEMPLATE, timeoutTicks = 20)
    public static void breakingMiddleBlockDropsItsContentsAndSplitsThePile(GameTestHelper helper) {
        StoragePileChecks.breakingMiddleBlockDropsItsContentsAndSplitsThePile(helper);
    }

    /** See {@link StoragePileChecks#comparatorReservesZeroForAnEmptyPile}. */
    @GameTest(template = GameTestSupport.TEMPLATE)
    public static void comparatorReservesZeroForAnEmptyPile(GameTestHelper helper) {
        StoragePileChecks.comparatorReservesZeroForAnEmptyPile(helper);
    }

    /** See {@link StoragePileChecks#comparatorContributionLoadsAndTracksMutations}. */
    @GameTest(template = GameTestSupport.TEMPLATE)
    public static void comparatorContributionLoadsAndTracksMutations(GameTestHelper helper) {
        StoragePileChecks.comparatorContributionLoadsAndTracksMutations(helper);
    }
}
