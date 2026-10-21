package com.github.crittscott.somestacks.gametest;

import com.github.crittscott.somestacks.block.StorageStackBE;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.items.IItemHandler;

import static com.github.crittscott.somestacks.gametest.GameTestScaffold.ORIGIN;
import static com.github.crittscott.somestacks.gametest.GameTestScaffold.check;
import static com.github.crittscott.somestacks.gametest.GameTestScaffold.checkEquals;
import static com.github.crittscott.somestacks.gametest.GameTestScaffold.count;
import static com.github.crittscott.somestacks.gametest.GameTestScaffold.placeStorage;

/**
 * Storage pile behavior in a live level: deposits filling partial stacks before empty ones, growth
 * when a pile fills, capability insertion addressing the specified slot, obstruction refusing
 * growth, and settling consolidating, packing, and sorting.
 */
public final class StoragePileGameTests {
    private StoragePileGameTests() {}

    /** See {@link StoragePileChecks#depositFillsCompatiblePartialBeforeEmptySlot}. */
    public static void depositFillsCompatiblePartialBeforeEmptySlot(GameTestHelper helper) {
        StoragePileChecks.depositFillsCompatiblePartialBeforeEmptySlot(helper);
    }

    /** See {@link StoragePileChecks#fullPileGrowsByOneBlock}. */
    public static void fullPileGrowsByOneBlock(GameTestHelper helper) {
        StoragePileChecks.fullPileGrowsByOneBlock(helper);
    }

    /**
     * To reproduce in-game: target a named Storage slot with item automation; that slot accepts the
     * item when compatible, and settling later packs it toward the base.
     */
    public static void capabilityInsertionAnswersForTheSlotItIsGiven(GameTestHelper helper) {
        AutomationChecks.storageInsertionAnswersForTheSlotItIsGiven(helper);
    }

    /**
     * To reproduce in-game: fill a Storage pile, obstruct the space above it, and insert through
     * item automation; it reports no room, accepts nothing, and leaves the obstruction intact.
     */
    public static void obstructionPreventsGrowthAndSimulationReportsNoRoom(GameTestHelper helper) {
        AutomationChecks.storageObstructionPreventsGrowthAndSimulationReportsNoRoom(helper);
    }

    /** See {@link StoragePileChecks#settleConsolidatesExactIdentityAndPacksDown}. */
    public static void settleConsolidatesExactIdentityAndPacksDown(GameTestHelper helper) {
        StoragePileChecks.settleConsolidatesExactIdentityAndPacksDown(helper);
    }

    /** See {@link StoragePileChecks#extractionSettlesOnScheduledBlockTick}. */
    public static void extractionSettlesOnScheduledBlockTick(GameTestHelper helper) {
        StoragePileChecks.extractionSettlesOnScheduledBlockTick(helper);
    }

    /** See {@link StoragePileChecks#emptyTemporaryPileDisappearsButPermanentPileRemains}. */
    public static void emptyTemporaryPileDisappearsButPermanentPileRemains(GameTestHelper helper) {
        StoragePileChecks.emptyTemporaryPileDisappearsButPermanentPileRemains(helper);
    }

    /**
     * To reproduce in-game: connect item automation separately to the lower and upper blocks of one
     * Storage pile; both connections expose the same items and capacity.
     */
    public static void capabilityFromEveryBlockAddressesTheSamePile(GameTestHelper helper) {
        StorageStackBE lower = placeStorage(helper, ORIGIN);
        StorageStackBE upper = placeStorage(helper, ORIGIN.above());
        lower.getItems().insertItem(0, new ItemStack(Items.STONE, 12));

        IItemHandler lowerCapability = GameTestSupport.capability(lower);
        IItemHandler upperCapability = GameTestSupport.capability(upper);

        checkEquals(12, lowerCapability.getStackInSlot(0).getCount(),
                "Lower capability count");
        checkEquals(12, upperCapability.getStackInSlot(0).getCount(),
                "Upper capability count");
        checkEquals(lowerCapability.getSlots(), upperCapability.getSlots(),
                "Capability shape");
        helper.succeed();
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
    public static void breakingMiddleBlockDropsItsContentsAndSplitsThePile(
            GameTestHelper helper) {
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
        StoragePileChecks.storageCapAppliesToPlayersAutomationAndSettlement(helper, GameTestSupport::offerAt);
    }

    /** See {@link StoragePileChecks#storageDiskMigrationPreservesOverCapContents}. */
    public static void storageDiskMigrationPreservesOverCapContents(GameTestHelper helper) {
        StoragePileChecks.storageDiskMigrationPreservesOverCapContents(helper);
    }

}
