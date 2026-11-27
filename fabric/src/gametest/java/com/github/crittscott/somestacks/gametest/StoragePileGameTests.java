package com.github.crittscott.somestacks.gametest;

import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;

/**
 * Storage pile behavior in a live level: deposits filling partial stacks before empty ones, growth
 * when a pile fills, storage insertion addressing the specified slot, obstruction refusing growth,
 * and settling consolidating, packing, and sorting.
 */
public final class StoragePileGameTests implements FabricGameTest {
    /** See {@link StoragePileChecks#depositFillsCompatiblePartialBeforeEmptySlot}. */
    @GameTest(template = FabricGameTestSupport.TEMPLATE)
    public void depositFillsCompatiblePartialBeforeEmptySlot(GameTestHelper helper) {
        StoragePileChecks.depositFillsCompatiblePartialBeforeEmptySlot(helper);
    }

    /** See {@link StoragePileChecks#fullPileGrowsByOneBlock}. */
    @GameTest(template = FabricGameTestSupport.TEMPLATE)
    public void fullPileGrowsByOneBlock(GameTestHelper helper) {
        StoragePileChecks.fullPileGrowsByOneBlock(
                helper, FabricGameTestSupport.playerFactory(helper));
    }

    /** See {@link AutomationChecks#storageInsertionAnswersForTheSlotItIsGiven}. */
    @GameTest(template = FabricGameTestSupport.TEMPLATE, timeoutTicks = 20)
    public void storageInsertionAnswersForTheSlotItIsGiven(GameTestHelper helper) {
        AutomationChecks.storageInsertionAnswersForTheSlotItIsGiven(
                helper, FabricGameTestSupport::automation);
    }

    /** See {@link AutomationChecks#storageObstructionPreventsGrowthAndSimulationReportsNoRoom}. */
    @GameTest(template = FabricGameTestSupport.TEMPLATE)
    public void obstructionPreventsGrowthAndSimulationReportsNoRoom(GameTestHelper helper) {
        AutomationChecks.storageObstructionPreventsGrowthAndSimulationReportsNoRoom(
                helper, FabricGameTestSupport::automation);
    }

    /** See {@link StoragePileChecks#settleConsolidatesExactIdentityAndPacksDown}. */
    @GameTest(template = FabricGameTestSupport.TEMPLATE)
    public void settleConsolidatesExactIdentityAndPacksDown(GameTestHelper helper) {
        StoragePileChecks.settleConsolidatesExactIdentityAndPacksDown(helper);
    }

    /** See {@link StoragePileChecks#settleWritesOnlyChangedCellsWithFreshStacks}. */
    @GameTest(template = FabricGameTestSupport.TEMPLATE)
    public void settleWritesOnlyChangedCellsWithFreshStacks(GameTestHelper helper) {
        StoragePileChecks.settleWritesOnlyChangedCellsWithFreshStacks(helper);
    }

    /** See {@link StoragePileChecks#storedLightIsPerOccupiedSlotAndCapped}. */
    @GameTest(template = FabricGameTestSupport.TEMPLATE, timeoutTicks = 20)
    public void storedLightIsPerOccupiedSlotAndCapped(GameTestHelper helper) {
        StoragePileChecks.storedLightIsPerOccupiedSlotAndCapped(helper);
    }

    /** See {@link StoragePileChecks#extractionSettlesOnScheduledBlockTick}. */
    @GameTest(template = FabricGameTestSupport.TEMPLATE, timeoutTicks = 20)
    public void extractionSettlesOnScheduledBlockTick(GameTestHelper helper) {
        StoragePileChecks.extractionSettlesOnScheduledBlockTick(
                helper, FabricGameTestSupport.playerFactory(helper));
    }

    /** See {@link StoragePileChecks#emptyTemporaryPileDisappearsButPermanentPileRemains}. */
    @GameTest(template = FabricGameTestSupport.TEMPLATE)
    public void emptyTemporaryPileDisappearsButPermanentPileRemains(GameTestHelper helper) {
        StoragePileChecks.emptyTemporaryPileDisappearsButPermanentPileRemains(helper);
    }

    /** See {@link AutomationChecks#storageFromEveryBlockAddressesTheSamePile}. */
    @GameTest(template = FabricGameTestSupport.TEMPLATE)
    public void storageFromEveryBlockAddressesTheSamePile(GameTestHelper helper) {
        AutomationChecks.storageFromEveryBlockAddressesTheSamePile(
                helper, FabricGameTestSupport::automation);
    }

    /** See {@link StoragePileChecks#comparatorReadsTheWholePileFromEveryBlock}. */
    @GameTest(template = FabricGameTestSupport.TEMPLATE)
    public void comparatorReadsTheWholePileFromEveryBlock(GameTestHelper helper) {
        StoragePileChecks.comparatorReadsTheWholePileFromEveryBlock(helper);
    }

    /** See {@link StoragePileChecks#ordinaryPlacementBelowPermanentPileRepairsDerivedState}. */
    @GameTest(template = FabricGameTestSupport.TEMPLATE, timeoutTicks = 20)
    public void ordinaryPlacementBelowPermanentPileRepairsDerivedState(GameTestHelper helper) {
        StoragePileChecks.ordinaryPlacementBelowPermanentPileRepairsDerivedState(helper);
    }

    /** See {@link StoragePileChecks#breakingMiddleBlockDropsItsContentsAndSplitsThePile}. */
    @GameTest(template = FabricGameTestSupport.TEMPLATE, timeoutTicks = 20)
    public void breakingMiddleBlockDropsItsContentsAndSplitsThePile(GameTestHelper helper) {
        StoragePileChecks.breakingMiddleBlockDropsItsContentsAndSplitsThePile(helper);
    }

    /** See {@link StoragePileChecks#comparatorReservesZeroForAnEmptyPile}. */
    @GameTest(template = FabricGameTestSupport.TEMPLATE)
    public void comparatorReservesZeroForAnEmptyPile(GameTestHelper helper) {
        StoragePileChecks.comparatorReservesZeroForAnEmptyPile(helper);
    }

    /** See {@link StoragePileChecks#comparatorContributionLoadsAndTracksMutations}. */
    @GameTest(template = FabricGameTestSupport.TEMPLATE)
    public void comparatorContributionLoadsAndTracksMutations(GameTestHelper helper) {
        StoragePileChecks.comparatorContributionLoadsAndTracksMutations(helper);
    }
}
