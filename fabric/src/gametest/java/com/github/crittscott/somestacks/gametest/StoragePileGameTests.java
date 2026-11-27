package com.github.crittscott.somestacks.gametest;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;

/**
 * Storage pile behavior in a live level: deposits filling partial stacks before empty ones, growth
 * when a pile fills, storage insertion addressing the specified slot, obstruction refusing growth,
 * and settling consolidating, packing, and sorting.
 */
public final class StoragePileGameTests {
    /** See {@link StoragePileChecks#depositFillsCompatiblePartialBeforeEmptySlot}. */
    @GameTest(structure = FabricGameTestSupport.TEMPLATE, maxTicks = 100)
    public void depositFillsCompatiblePartialBeforeEmptySlot(GameTestHelper helper) {
        StoragePileChecks.depositFillsCompatiblePartialBeforeEmptySlot(helper);
    }

    /** See {@link StoragePileChecks#fullPileGrowsByOneBlock}. */
    @GameTest(structure = FabricGameTestSupport.TEMPLATE, maxTicks = 100)
    public void fullPileGrowsByOneBlock(GameTestHelper helper) {
        StoragePileChecks.fullPileGrowsByOneBlock(
                helper, FabricGameTestSupport.playerFactory(helper));
    }

    /** See {@link AutomationChecks#storageInsertionAnswersForTheSlotItIsGiven}. */
    @GameTest(structure = FabricGameTestSupport.TEMPLATE, maxTicks = 100)
    public void storageInsertionAnswersForTheSlotItIsGiven(GameTestHelper helper) {
        AutomationChecks.storageInsertionAnswersForTheSlotItIsGiven(
                helper, FabricGameTestSupport::automation);
    }

    /** See {@link AutomationChecks#storageObstructionPreventsGrowthAndSimulationReportsNoRoom}. */
    @GameTest(structure = FabricGameTestSupport.TEMPLATE, maxTicks = 100)
    public void obstructionPreventsGrowthAndSimulationReportsNoRoom(GameTestHelper helper) {
        AutomationChecks.storageObstructionPreventsGrowthAndSimulationReportsNoRoom(
                helper, FabricGameTestSupport::automation);
    }

    /** See {@link StoragePileChecks#settleConsolidatesExactIdentityAndPacksDown}. */
    @GameTest(structure = FabricGameTestSupport.TEMPLATE, maxTicks = 100)
    public void settleConsolidatesExactIdentityAndPacksDown(GameTestHelper helper) {
        StoragePileChecks.settleConsolidatesExactIdentityAndPacksDown(helper);
    }

    /** See {@link StoragePileChecks#settleWritesOnlyChangedCellsWithFreshStacks}. */
    @GameTest(structure = FabricGameTestSupport.TEMPLATE, maxTicks = 100)
    public void settleWritesOnlyChangedCellsWithFreshStacks(GameTestHelper helper) {
        StoragePileChecks.settleWritesOnlyChangedCellsWithFreshStacks(helper);
    }

    /** See {@link StoragePileChecks#storedLightIsPerOccupiedSlotAndCapped}. */
    @GameTest(structure = FabricGameTestSupport.TEMPLATE, maxTicks = 20)
    public void storedLightIsPerOccupiedSlotAndCapped(GameTestHelper helper) {
        StoragePileChecks.storedLightIsPerOccupiedSlotAndCapped(helper);
    }

    /** See {@link StoragePileChecks#extractionSettlesOnScheduledBlockTick}. */
    @GameTest(structure = FabricGameTestSupport.TEMPLATE, maxTicks = 20)
    public void extractionSettlesOnScheduledBlockTick(GameTestHelper helper) {
        StoragePileChecks.extractionSettlesOnScheduledBlockTick(
                helper, FabricGameTestSupport.playerFactory(helper));
    }

    /** See {@link StoragePileChecks#emptyTemporaryPileDisappearsButPermanentPileRemains}. */
    @GameTest(structure = FabricGameTestSupport.TEMPLATE, maxTicks = 100)
    public void emptyTemporaryPileDisappearsButPermanentPileRemains(GameTestHelper helper) {
        StoragePileChecks.emptyTemporaryPileDisappearsButPermanentPileRemains(helper);
    }

    /** See {@link AutomationChecks#storageFromEveryBlockAddressesTheSamePile}. */
    @GameTest(structure = FabricGameTestSupport.TEMPLATE, maxTicks = 100)
    public void storageFromEveryBlockAddressesTheSamePile(GameTestHelper helper) {
        AutomationChecks.storageFromEveryBlockAddressesTheSamePile(
                helper, FabricGameTestSupport::automation);
    }

    /** See {@link StoragePileChecks#comparatorReadsTheWholePileFromEveryBlock}. */
    @GameTest(structure = FabricGameTestSupport.TEMPLATE, maxTicks = 100)
    public void comparatorReadsTheWholePileFromEveryBlock(GameTestHelper helper) {
        StoragePileChecks.comparatorReadsTheWholePileFromEveryBlock(helper);
    }

    /** See {@link StoragePileChecks#ordinaryPlacementBelowPermanentPileRepairsDerivedState}. */
    @GameTest(structure = FabricGameTestSupport.TEMPLATE, maxTicks = 20)
    public void ordinaryPlacementBelowPermanentPileRepairsDerivedState(GameTestHelper helper) {
        StoragePileChecks.ordinaryPlacementBelowPermanentPileRepairsDerivedState(helper);
    }

    /** See {@link StoragePileChecks#breakingMiddleBlockDropsItsContentsAndSplitsThePile}. */
    @GameTest(structure = FabricGameTestSupport.TEMPLATE, maxTicks = 20)
    public void breakingMiddleBlockDropsItsContentsAndSplitsThePile(GameTestHelper helper) {
        StoragePileChecks.breakingMiddleBlockDropsItsContentsAndSplitsThePile(helper);
    }

    /** See {@link StoragePileChecks#comparatorReservesZeroForAnEmptyPile}. */
    @GameTest(structure = FabricGameTestSupport.TEMPLATE, maxTicks = 100)
    public void comparatorReservesZeroForAnEmptyPile(GameTestHelper helper) {
        StoragePileChecks.comparatorReservesZeroForAnEmptyPile(helper);
    }

    /** See {@link StoragePileChecks#comparatorContributionLoadsAndTracksMutations}. */
    @GameTest(structure = FabricGameTestSupport.TEMPLATE, maxTicks = 100)
    public void comparatorContributionLoadsAndTracksMutations(GameTestHelper helper) {
        StoragePileChecks.comparatorContributionLoadsAndTracksMutations(helper);
    }

    /** See {@link StoragePileChecks#storageCapAppliesToPlayersAutomationAndSettlement}. */
    @GameTest(structure = FabricGameTestSupport.TEMPLATE, maxTicks = 100)
    public void storageCapAppliesToPlayersAutomationAndSettlement(GameTestHelper helper) {
        StoragePileChecks.storageCapAppliesToPlayersAutomationAndSettlement(
                helper, FabricGameTestSupport::automation);
    }

    /** See {@link StoragePileChecks#storageDiskMigrationPreservesOverCapContents}. */
    @GameTest(structure = FabricGameTestSupport.TEMPLATE, maxTicks = 100)
    public void storageDiskMigrationPreservesOverCapContents(GameTestHelper helper) {
        StoragePileChecks.storageDiskMigrationPreservesOverCapContents(helper);
    }
}
