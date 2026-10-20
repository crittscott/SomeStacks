package com.github.crittscott.somestacks.gametest;

import com.github.crittscott.somestacks.block.StorageStackBE;
import net.fabricmc.fabric.api.transfer.v1.item.ItemVariant;
import net.fabricmc.fabric.api.transfer.v1.storage.SlottedStorage;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import static com.github.crittscott.somestacks.gametest.GameTestScaffold.ORIGIN;
import static com.github.crittscott.somestacks.gametest.GameTestScaffold.check;
import static com.github.crittscott.somestacks.gametest.GameTestScaffold.checkEquals;
import static com.github.crittscott.somestacks.gametest.GameTestScaffold.count;
import static com.github.crittscott.somestacks.gametest.GameTestScaffold.placeStorage;

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
        StoragePileChecks.fullPileGrowsByOneBlock(helper);
    }

    /**
     * To reproduce in-game: target a named Storage slot with item automation; that slot accepts the
     * item when compatible, and settling later packs it toward the base.
     */
    @GameTest(structure = FabricGameTestSupport.TEMPLATE, maxTicks = 100)
    public void storageInsertionAnswersForTheSlotItIsGiven(GameTestHelper helper) {
        AutomationChecks.storageInsertionAnswersForTheSlotItIsGiven(helper);
    }

    /**
     * To reproduce in-game: fill a Storage pile, obstruct the space above it, and insert through
     * item automation; it reports no room, accepts nothing, and leaves the obstruction intact.
     */
    @GameTest(structure = FabricGameTestSupport.TEMPLATE, maxTicks = 100)
    public void obstructionPreventsGrowthAndSimulationReportsNoRoom(GameTestHelper helper) {
        AutomationChecks.storageObstructionPreventsGrowthAndSimulationReportsNoRoom(helper);
    }

    /** See {@link StoragePileChecks#settleConsolidatesExactIdentityAndPacksDown}. */
    @GameTest(structure = FabricGameTestSupport.TEMPLATE, maxTicks = 100)
    public void settleConsolidatesExactIdentityAndPacksDown(GameTestHelper helper) {
        StoragePileChecks.settleConsolidatesExactIdentityAndPacksDown(helper);
    }

    /** See {@link StoragePileChecks#extractionSettlesOnScheduledBlockTick}. */
    @GameTest(structure = FabricGameTestSupport.TEMPLATE, maxTicks = 20)
    public void extractionSettlesOnScheduledBlockTick(GameTestHelper helper) {
        StoragePileChecks.extractionSettlesOnScheduledBlockTick(helper);
    }

    /** See {@link StoragePileChecks#emptyTemporaryPileDisappearsButPermanentPileRemains}. */
    @GameTest(structure = FabricGameTestSupport.TEMPLATE, maxTicks = 100)
    public void emptyTemporaryPileDisappearsButPermanentPileRemains(GameTestHelper helper) {
        StoragePileChecks.emptyTemporaryPileDisappearsButPermanentPileRemains(helper);
    }

    /**
     * To reproduce in-game: connect item automation separately to the lower and upper blocks of one
     * Storage pile; both connections expose the same items and capacity.
     */
    @GameTest(structure = FabricGameTestSupport.TEMPLATE, maxTicks = 100)
    public void storageFromEveryBlockAddressesTheSamePile(GameTestHelper helper) {
        StorageStackBE lower = placeStorage(helper, ORIGIN);
        StorageStackBE upper = placeStorage(helper, ORIGIN.above());
        lower.getItems().insertItem(0, new ItemStack(Items.STONE, 12));

        SlottedStorage<ItemVariant> lowerStorage = FabricGameTestSupport.storage(lower);
        SlottedStorage<ItemVariant> upperStorage = FabricGameTestSupport.storage(upper);

        checkEquals(12, FabricGameTestSupport.stackAt(lowerStorage, 0).getCount(),
                "Lower storage count");
        checkEquals(12, FabricGameTestSupport.stackAt(upperStorage, 0).getCount(),
                "Upper storage count");
        checkEquals(lowerStorage.getSlotCount(), upperStorage.getSlotCount(),
                "Storage shape");
        helper.succeed();
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

}
