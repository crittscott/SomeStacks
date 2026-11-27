package com.github.crittscott.somestacks.gametest;

import static com.github.crittscott.somestacks.gametest.GameTestScaffold.ORIGIN;
import static com.github.crittscott.somestacks.gametest.GameTestScaffold.check;
import static com.github.crittscott.somestacks.gametest.GameTestScaffold.placeBar;
import static com.github.crittscott.somestacks.gametest.GameTestScaffold.placeSingles;
import static com.github.crittscott.somestacks.gametest.GameTestScaffold.placeStorage;

import com.github.crittscott.somestacks.block.BarStackBE;
import com.github.crittscott.somestacks.block.SinglesStackBE;
import com.github.crittscott.somestacks.block.StorageStackBE;

import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;

/**
 * The automation surface and the saved state behind it: the item handler exposed on every side, a
 * run advertising its reachable headroom, and each type's update tag round-tripping the contents
 * and presentation state it is responsible for.
 */
public final class AutomationAndPersistenceGameTests {
    private AutomationAndPersistenceGameTests() {}

    /**
     * To reproduce in-game: connect sided item automation to each face of every stack type and
     * verify every face exposes the same inventory.
     */
    public static void itemCapabilityIsAvailableFromEverySide(GameTestHelper helper) {
        StorageStackBE storage = placeStorage(helper, ORIGIN);
        SinglesStackBE singles = placeSingles(helper, ORIGIN.east(3));
        BarStackBE bar = placeBar(helper, ORIGIN.east(6));

        for (Direction side : Direction.values()) {
            check(
                    GameTestSupport.capabilityPresent(storage, side),
                    "Storage capability missing on " + side);
            check(
                    GameTestSupport.capabilityPresent(singles, side),
                    "Singles capability missing on " + side);
            check(
                    GameTestSupport.capabilityPresent(bar, side),
                    "Bar capability missing on " + side);
        }
        check(
                GameTestSupport.capabilityPresent(storage, null),
                "Storage capability missing on null side");
        check(
                GameTestSupport.capabilityPresent(singles, null),
                "Singles capability missing on null side");
        check(GameTestSupport.capabilityPresent(bar, null), "Bar capability missing on null side");
        helper.succeed();
    }

    /** See {@link AutomationChecks#singleBlocksAdvertiseConfiguredHeadroom}. */
    public static void singleBlockCapabilitiesAdvertiseConfiguredHeadroom(GameTestHelper helper) {
        AutomationChecks.singleBlocksAdvertiseConfiguredHeadroom(
                helper, GameTestSupport::automation);
    }

    /**
     * See {@link
     * AutomationAndPersistenceChecks#storageDiskSaveRoundTripsItemsRotationAndPermanence}.
     */
    public static void storageDiskSaveRoundTripsItemsRotationAndPermanence(GameTestHelper helper) {
        AutomationAndPersistenceChecks.storageDiskSaveRoundTripsItemsRotationAndPermanence(helper);
    }

    /**
     * See {@link AutomationAndPersistenceChecks#singlesDiskSaveRoundTripsItemsAndBothRotations}.
     */
    public static void singlesDiskSaveRoundTripsItemsAndBothRotations(GameTestHelper helper) {
        AutomationAndPersistenceChecks.singlesDiskSaveRoundTripsItemsAndBothRotations(helper);
    }

    /** See {@link AutomationAndPersistenceChecks#barDiskSaveRoundTripsItems}. */
    public static void barDiskSaveRoundTripsItems(GameTestHelper helper) {
        AutomationAndPersistenceChecks.barDiskSaveRoundTripsItems(helper);
    }

    /**
     * See {@link AutomationAndPersistenceChecks#updateTagsCarryRenderStateAndOmitServerOnlyData}.
     */
    public static void updateTagsCarryRenderStateAndOmitServerOnlyData(GameTestHelper helper) {
        AutomationAndPersistenceChecks.updateTagsCarryRenderStateAndOmitServerOnlyData(helper);
    }

    /** See {@link AutomationAndPersistenceChecks#updateTagOmitsContainerContentsAndBookPages}. */
    public static void updateTagOmitsContainerContentsAndBookPages(GameTestHelper helper) {
        AutomationAndPersistenceChecks.updateTagOmitsContainerContentsAndBookPages(helper);
    }

    /** See {@link AutomationAndPersistenceChecks#unreadableSavedItemsAreKeptAsideOnDisk}. */
    public static void unreadableSavedItemsAreKeptAsideOnDisk(GameTestHelper helper) {
        AutomationAndPersistenceChecks.unreadableSavedItemsAreKeptAsideOnDisk(helper);
    }

    /** See {@link AutomationAndPersistenceChecks#setAsideItemsRecoverIntoFreeSlots}. */
    public static void setAsideItemsRecoverIntoFreeSlots(GameTestHelper helper) {
        AutomationAndPersistenceChecks.setAsideItemsRecoverIntoFreeSlots(helper);
    }

    /** See {@link AutomationAndPersistenceChecks#unversionedSaveUpgradesItemsOnLoad}. */
    public static void unversionedSaveUpgradesItemsOnLoad(GameTestHelper helper) {
        AutomationAndPersistenceChecks.unversionedSaveUpgradesItemsOnLoad(helper);
    }

    /** See {@link AutomationAndPersistenceChecks#releasedSinglesFormatLoads}. */
    public static void releasedSinglesFormatLoads(GameTestHelper helper) {
        AutomationAndPersistenceChecks.releasedSinglesFormatLoads(helper);
    }

    /** See {@link AutomationAndPersistenceChecks#releasedBarFormatLoads}. */
    public static void releasedBarFormatLoads(GameTestHelper helper) {
        AutomationAndPersistenceChecks.releasedBarFormatLoads(helper);
    }

    /**
     * See {@link AutomationAndPersistenceChecks#structureRotationTransformsRotatableStackFacing}.
     */
    public static void structureRotationTransformsRotatableStackFacing(GameTestHelper helper) {
        AutomationAndPersistenceChecks.structureRotationTransformsRotatableStackFacing(helper);
    }

    /** See {@link AutomationAndPersistenceChecks#cachedShapesInvalidateWhenContentsChange}. */
    public static void cachedShapesInvalidateWhenContentsChange(GameTestHelper helper) {
        AutomationAndPersistenceChecks.cachedShapesInvalidateWhenContentsChange(
                helper, GameTestSupport.playerFactory(helper));
    }

    /** See {@link AutomationChecks#reentrantStructuralEditIsRefused}. */
    public static void reentrantStructuralEditIsRefused(GameTestHelper helper) {
        AutomationChecks.reentrantStructuralEditIsRefused(helper, GameTestSupport::automation);
    }

    /** See {@link AutomationChecks#singlesSimulationDoesNotMutateAnyStack}. */
    public static void capabilitySimulationDoesNotMutateAnyStack(GameTestHelper helper) {
        AutomationChecks.singlesSimulationDoesNotMutateAnyStack(
                helper, GameTestSupport::automation);
    }

    /** See {@link SynchronizationChecks#clientInventoryUpdatesRejectMalformedDataAtomically}. */
    public static void clientInventoryUpdatesRejectMalformedDataAtomically(GameTestHelper helper) {
        SynchronizationChecks.clientInventoryUpdatesRejectMalformedDataAtomically(helper);
    }

    /** See {@link SynchronizationChecks#clientSinglesUpdatesValidateRotationBeforeInventory}. */
    public static void clientSinglesUpdatesValidateRotationBeforeInventory(GameTestHelper helper) {
        SynchronizationChecks.clientSinglesUpdatesValidateRotationBeforeInventory(helper);
    }
}
