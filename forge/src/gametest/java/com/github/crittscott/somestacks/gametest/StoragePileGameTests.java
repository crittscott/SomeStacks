package com.github.crittscott.somestacks.gametest;

import net.minecraft.gametest.framework.GameTestHelper;

/**
 * Storage pile behavior in a live level: deposits filling partial stacks before empty ones, growth
 * when a pile fills, capability insertion addressing the specified slot, obstruction refusing
 * growth, and settling consolidating, packing, and sorting.
 */
public final class StoragePileGameTests {
    public StoragePileGameTests() {}

    /** See {@link StoragePileChecks#depositFillsCompatiblePartialBeforeEmptySlot}. */
    public static void depositFillsCompatiblePartialBeforeEmptySlot(GameTestHelper helper) {
        StoragePileChecks.depositFillsCompatiblePartialBeforeEmptySlot(helper);
    }

    /** See {@link StoragePileChecks#fullPileGrowsByOneBlock}. */
    public static void fullPileGrowsByOneBlock(GameTestHelper helper) {
        StoragePileChecks.fullPileGrowsByOneBlock(helper, GameTestSupport.playerFactory(helper));
    }

    /** See {@link AutomationChecks#storageInsertionAnswersForTheSlotItIsGiven}. */
    public static void capabilityInsertionAnswersForTheSlotItIsGiven(GameTestHelper helper) {
        AutomationChecks.storageInsertionAnswersForTheSlotItIsGiven(
                helper, GameTestSupport::automation);
    }

    /** See {@link AutomationChecks#storageObstructionPreventsGrowthAndSimulationReportsNoRoom}. */
    public static void obstructionPreventsGrowthAndSimulationReportsNoRoom(GameTestHelper helper) {
        AutomationChecks.storageObstructionPreventsGrowthAndSimulationReportsNoRoom(
                helper, GameTestSupport::automation);
    }

    /** See {@link StoragePileChecks#settleConsolidatesExactIdentityAndPacksDown}. */
    public static void settleConsolidatesExactIdentityAndPacksDown(GameTestHelper helper) {
        StoragePileChecks.settleConsolidatesExactIdentityAndPacksDown(helper);
    }

    /** See {@link StoragePileChecks#settleWritesOnlyChangedCellsWithFreshStacks}. */
    public static void settleWritesOnlyChangedCellsWithFreshStacks(GameTestHelper helper) {
        StoragePileChecks.settleWritesOnlyChangedCellsWithFreshStacks(helper);
    }

    /** See {@link StoragePileChecks#storedLightIsPerOccupiedSlotAndCapped}. */
    public static void storedLightIsPerOccupiedSlotAndCapped(GameTestHelper helper) {
        StoragePileChecks.storedLightIsPerOccupiedSlotAndCapped(helper);
    }

    /** See {@link StoragePileChecks#extractionSettlesOnScheduledBlockTick}. */
    public static void extractionSettlesOnScheduledBlockTick(GameTestHelper helper) {
        StoragePileChecks.extractionSettlesOnScheduledBlockTick(
                helper, GameTestSupport.playerFactory(helper));
    }

    /** See {@link StoragePileChecks#emptyTemporaryPileDisappearsButPermanentPileRemains}. */
    public static void emptyTemporaryPileDisappearsButPermanentPileRemains(GameTestHelper helper) {
        StoragePileChecks.emptyTemporaryPileDisappearsButPermanentPileRemains(helper);
    }

    /** See {@link AutomationChecks#storageFromEveryBlockAddressesTheSamePile}. */
    public static void capabilityFromEveryBlockAddressesTheSamePile(GameTestHelper helper) {
        AutomationChecks.storageFromEveryBlockAddressesTheSamePile(
                helper, GameTestSupport::automation);
    }

    /** See {@link StoragePileChecks#comparatorReadsTheWholePileFromEveryBlock}. */
    public static void comparatorReadsTheWholePileFromEveryBlock(GameTestHelper helper) {
        StoragePileChecks.comparatorReadsTheWholePileFromEveryBlock(helper);
    }

    /** See {@link StoragePileChecks#ordinaryPlacementBelowPermanentPileRepairsDerivedState}. */
    public static void ordinaryPlacementBelowPermanentPileRepairsDerivedState(
            GameTestHelper helper) {
        StoragePileChecks.ordinaryPlacementBelowPermanentPileRepairsDerivedState(helper);
    }

    /** See {@link StoragePileChecks#breakingMiddleBlockDropsItsContentsAndSplitsThePile}. */
    public static void breakingMiddleBlockDropsItsContentsAndSplitsThePile(GameTestHelper helper) {
        StoragePileChecks.breakingMiddleBlockDropsItsContentsAndSplitsThePile(helper);
    }

    /** See {@link StoragePileChecks#comparatorReservesZeroForAnEmptyPile}. */
    public static void comparatorReservesZeroForAnEmptyPile(GameTestHelper helper) {
        StoragePileChecks.comparatorReservesZeroForAnEmptyPile(helper);
    }

    /** See {@link StoragePileChecks#comparatorContributionLoadsAndTracksMutations}. */
    public static void comparatorContributionLoadsAndTracksMutations(GameTestHelper helper) {
        StoragePileChecks.comparatorContributionLoadsAndTracksMutations(helper);
    }

    /** See {@link StoragePileChecks#storageCapAppliesToPlayersAutomationAndSettlement}. */
    public static void storageCapAppliesToPlayersAutomationAndSettlement(GameTestHelper helper) {
        StoragePileChecks.storageCapAppliesToPlayersAutomationAndSettlement(
                helper, GameTestSupport::automation);
    }

    /** See {@link StoragePileChecks#storageDiskMigrationPreservesOverCapContents}. */
    public static void storageDiskMigrationPreservesOverCapContents(GameTestHelper helper) {
        StoragePileChecks.storageDiskMigrationPreservesOverCapContents(helper);
    }
}
