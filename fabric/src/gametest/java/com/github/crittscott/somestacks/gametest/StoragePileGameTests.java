package com.github.crittscott.somestacks.gametest;

import net.fabricmc.fabric.api.entity.FakePlayer;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;

/**
 * Storage pile behavior in a live level: deposits filling partial stacks before empty ones, growth
 * when a pile fills, storage insertion addressing the specified slot, obstruction refusing growth,
 * and settling consolidating, packing, and sorting.
 */
public final class StoragePileGameTests implements FabricGameTest {
    /**
     * Loader entry point for {@link
     * StoragePileChecks#depositFillsCompatiblePartialBeforeEmptySlot}; see that method for the
     * scenario, manual check, and any internal-only assertions.
     */
    @GameTest(template = FabricGameTestSupport.TEMPLATE)
    public void depositFillsCompatiblePartialBeforeEmptySlot(GameTestHelper helper) {
        StoragePileChecks.depositFillsCompatiblePartialBeforeEmptySlot(helper);
    }

    /**
     * Loader entry point for {@link StoragePileChecks#fullPileGrowsByOneBlock}; see that method for
     * the scenario, manual check, and any internal-only assertions.
     */
    @GameTest(template = FabricGameTestSupport.TEMPLATE)
    public void fullPileGrowsByOneBlock(GameTestHelper helper) {
        StoragePileChecks.fullPileGrowsByOneBlock(
                helper, FabricGameTestSupport.players(helper), FabricGameTestSupport::click);
    }

    /** See {@link StoragePileChecks#growthRequiresBuildPermission}. */
    @GameTest(template = FabricGameTestSupport.TEMPLATE)
    public void growthRequiresBuildPermission(GameTestHelper helper) {
        StoragePileChecks.growthRequiresBuildPermission(helper, FakePlayer.get(helper.getLevel()));
    }

    /** See {@link StoragePileChecks#placementChoosesFacingBeforeJoining}. */
    @GameTest(template = FabricGameTestSupport.TEMPLATE)
    public void placementChoosesFacingBeforeJoining(GameTestHelper helper) {
        StoragePileChecks.placementChoosesFacingBeforeJoining(helper);
    }

    /**
     * Loader entry point for {@link StoragePileChecks#grownBlockTakesTheBaseFacing}; see that
     * method for the scenario and manual check.
     */
    @GameTest(template = FabricGameTestSupport.TEMPLATE)
    public void grownBlockTakesTheBaseFacing(GameTestHelper helper) {
        StoragePileChecks.grownBlockTakesTheBaseFacing(
                helper, FabricGameTestSupport.players(helper), FabricGameTestSupport::click);
    }

    /**
     * Loader entry point for {@link
     * StoragePileChecks#capabilityInsertionAnswersForTheSlotItIsGiven}; see that method for the
     * scenario, manual check, and any internal-only assertions.
     */
    @GameTest(template = FabricGameTestSupport.TEMPLATE)
    public void capabilityInsertionAnswersForTheSlotItIsGiven(GameTestHelper helper) {
        StoragePileChecks.capabilityInsertionAnswersForTheSlotItIsGiven(
                helper, FabricGameTestSupport::automation);
    }

    /**
     * Loader entry point for {@link
     * StoragePileChecks#obstructionPreventsGrowthAndSimulationReportsNoRoom}; see that method for
     * the scenario, manual check, and any internal-only assertions.
     */
    @GameTest(template = FabricGameTestSupport.TEMPLATE)
    public void obstructionPreventsGrowthAndSimulationReportsNoRoom(GameTestHelper helper) {
        StoragePileChecks.obstructionPreventsGrowthAndSimulationReportsNoRoom(
                helper, FabricGameTestSupport::automation);
    }

    /**
     * Loader entry point for {@link StoragePileChecks#settleConsolidatesExactIdentityAndPacksDown};
     * see that method for the scenario, manual check, and any internal-only assertions.
     */
    @GameTest(template = FabricGameTestSupport.TEMPLATE)
    public void settleConsolidatesExactIdentityAndPacksDown(GameTestHelper helper) {
        StoragePileChecks.settleConsolidatesExactIdentityAndPacksDown(helper);
    }

    /** Loader entry point for {@link StoragePileChecks#settleMergesOnlyIdenticalNames}. */
    @GameTest(template = FabricGameTestSupport.TEMPLATE)
    public void settleMergesOnlyIdenticalNames(GameTestHelper helper) {
        StoragePileChecks.settleMergesOnlyIdenticalNames(helper);
    }

    /**
     * Loader entry point for {@link StoragePileChecks#extractionSettlesOnScheduledBlockTick}; see
     * that method for the scenario, manual check, and any internal-only assertions.
     */
    @GameTest(template = FabricGameTestSupport.TEMPLATE, timeoutTicks = 20)
    public void extractionSettlesOnScheduledBlockTick(GameTestHelper helper) {
        StoragePileChecks.extractionSettlesOnScheduledBlockTick(
                helper, FabricGameTestSupport.players(helper), FabricGameTestSupport::click);
    }

    /**
     * Loader entry point for {@link
     * StoragePileChecks#emptyTemporaryPileDisappearsButPermanentPileRemains}; see that method for
     * the scenario, manual check, and any internal-only assertions.
     */
    @GameTest(template = FabricGameTestSupport.TEMPLATE)
    public void emptyTemporaryPileDisappearsButPermanentPileRemains(GameTestHelper helper) {
        StoragePileChecks.emptyTemporaryPileDisappearsButPermanentPileRemains(helper);
    }

    /**
     * Loader entry point for {@link
     * StoragePileChecks#capabilityFromEveryBlockAddressesTheSamePile}; see that method for the
     * scenario, manual check, and any internal-only assertions.
     */
    @GameTest(template = FabricGameTestSupport.TEMPLATE)
    public void capabilityFromEveryBlockAddressesTheSamePile(GameTestHelper helper) {
        StoragePileChecks.capabilityFromEveryBlockAddressesTheSamePile(
                helper, FabricGameTestSupport::automation);
    }

    /**
     * Loader entry point for {@link StoragePileChecks#comparatorReadsTheWholePileFromEveryBlock};
     * see that method for the scenario, manual check, and any internal-only assertions.
     */
    @GameTest(template = FabricGameTestSupport.TEMPLATE)
    public void comparatorReadsTheWholePileFromEveryBlock(GameTestHelper helper) {
        StoragePileChecks.comparatorReadsTheWholePileFromEveryBlock(helper);
    }

    /**
     * Loader entry point for {@link
     * StoragePileChecks#ordinaryPlacementBelowPermanentPileRepairsDerivedState}; see that method
     * for the scenario, manual check, and any internal-only assertions.
     */
    @GameTest(template = FabricGameTestSupport.TEMPLATE, timeoutTicks = 20)
    public void ordinaryPlacementBelowPermanentPileRepairsDerivedState(GameTestHelper helper) {
        StoragePileChecks.ordinaryPlacementBelowPermanentPileRepairsDerivedState(helper);
    }

    /**
     * See {@link StoragePileChecks#middleRemovalSettlesAndPublishesBothSurvivingPiles} for the
     * split scenario and manual procedure.
     */
    @GameTest(template = FabricGameTestSupport.TEMPLATE)
    public void middleRemovalSettlesAndPublishesBothSurvivingPiles(GameTestHelper helper) {
        StoragePileChecks.middleRemovalSettlesAndPublishesBothSurvivingPiles(helper);
    }
}
