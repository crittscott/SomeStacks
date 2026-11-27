package com.github.crittscott.somestacks.gametest;

import static com.github.crittscott.somestacks.gametest.GameTestScaffold.ORIGIN;
import static com.github.crittscott.somestacks.gametest.GameTestScaffold.check;
import static com.github.crittscott.somestacks.gametest.GameTestScaffold.placeBar;
import static com.github.crittscott.somestacks.gametest.GameTestScaffold.placeSingles;
import static com.github.crittscott.somestacks.gametest.GameTestScaffold.placeStorage;

import com.github.crittscott.somestacks.SomeStacksNeoForge;
import com.github.crittscott.somestacks.block.BarStackBE;
import com.github.crittscott.somestacks.block.SinglesStackBE;
import com.github.crittscott.somestacks.block.StorageStackBE;

import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * The automation surface and the saved state behind it: the item handler exposed on every side, a
 * run advertising its reachable headroom, and each type's update tag round-tripping the contents
 * and presentation state it is responsible for.
 */
@GameTestHolder(SomeStacksNeoForge.MODID)
@PrefixGameTestTemplate(false)
public final class CapabilityAndPersistenceGameTests {
    private CapabilityAndPersistenceGameTests() {}

    /**
     * To reproduce in-game: connect sided item automation to each face of every stack type and
     * verify every face exposes the same inventory.
     */
    @GameTest(template = GameTestSupport.TEMPLATE)
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
    @GameTest(template = GameTestSupport.TEMPLATE)
    public static void singleBlockCapabilitiesAdvertiseConfiguredHeadroom(GameTestHelper helper) {
        AutomationChecks.singleBlocksAdvertiseConfiguredHeadroom(
                helper, GameTestSupport::automation);
    }

    /**
     * See {@link
     * CapabilityAndPersistenceChecks#storageDiskSaveRoundTripsItemsRotationAndPermanence}.
     */
    @GameTest(template = GameTestSupport.TEMPLATE)
    public static void storageDiskSaveRoundTripsItemsRotationAndPermanence(GameTestHelper helper) {
        CapabilityAndPersistenceChecks.storageDiskSaveRoundTripsItemsRotationAndPermanence(helper);
    }

    /**
     * See {@link CapabilityAndPersistenceChecks#singlesDiskSaveRoundTripsItemsAndBothRotations}.
     */
    @GameTest(template = GameTestSupport.TEMPLATE)
    public static void singlesDiskSaveRoundTripsItemsAndBothRotations(GameTestHelper helper) {
        CapabilityAndPersistenceChecks.singlesDiskSaveRoundTripsItemsAndBothRotations(helper);
    }

    /** See {@link CapabilityAndPersistenceChecks#barDiskSaveRoundTripsItems}. */
    @GameTest(template = GameTestSupport.TEMPLATE)
    public static void barDiskSaveRoundTripsItems(GameTestHelper helper) {
        CapabilityAndPersistenceChecks.barDiskSaveRoundTripsItems(helper);
    }

    /**
     * See {@link CapabilityAndPersistenceChecks#updateTagsCarryRenderStateAndOmitServerOnlyData}.
     */
    @GameTest(template = GameTestSupport.TEMPLATE)
    public static void updateTagsCarryRenderStateAndOmitServerOnlyData(GameTestHelper helper) {
        CapabilityAndPersistenceChecks.updateTagsCarryRenderStateAndOmitServerOnlyData(helper);
    }

    /** See {@link CapabilityAndPersistenceChecks#unreadableSavedItemsAreKeptAsideOnDisk}. */
    @GameTest(template = GameTestSupport.TEMPLATE)
    public static void unreadableSavedItemsAreKeptAsideOnDisk(GameTestHelper helper) {
        CapabilityAndPersistenceChecks.unreadableSavedItemsAreKeptAsideOnDisk(helper);
    }

    /** See {@link CapabilityAndPersistenceChecks#setAsideItemsRecoverIntoFreeSlots}. */
    @GameTest(template = GameTestSupport.TEMPLATE)
    public static void setAsideItemsRecoverIntoFreeSlots(GameTestHelper helper) {
        CapabilityAndPersistenceChecks.setAsideItemsRecoverIntoFreeSlots(helper);
    }

    /** See {@link CapabilityAndPersistenceChecks#unversionedSaveUpgradesItemsOnLoad}. */
    @GameTest(template = GameTestSupport.TEMPLATE)
    public static void unversionedSaveUpgradesItemsOnLoad(GameTestHelper helper) {
        CapabilityAndPersistenceChecks.unversionedSaveUpgradesItemsOnLoad(helper);
    }

    /**
     * See {@link CapabilityAndPersistenceChecks#structureRotationTransformsRotatableStackFacing}.
     */
    @GameTest(template = GameTestSupport.TEMPLATE)
    public static void structureRotationTransformsRotatableStackFacing(GameTestHelper helper) {
        CapabilityAndPersistenceChecks.structureRotationTransformsRotatableStackFacing(helper);
    }

    /** See {@link CapabilityAndPersistenceChecks#cachedShapesInvalidateWhenContentsChange}. */
    @GameTest(template = GameTestSupport.TEMPLATE)
    public static void cachedShapesInvalidateWhenContentsChange(GameTestHelper helper) {
        CapabilityAndPersistenceChecks.cachedShapesInvalidateWhenContentsChange(
                helper, GameTestSupport.playerFactory(helper));
    }

    /** See {@link AutomationChecks#reentrantStructuralEditIsRefused}. */
    @GameTest(template = GameTestSupport.TEMPLATE)
    public static void reentrantStructuralEditIsRefused(GameTestHelper helper) {
        AutomationChecks.reentrantStructuralEditIsRefused(helper, GameTestSupport::automation);
    }

    /** See {@link AutomationChecks#singlesSimulationDoesNotMutateAnyStack}. */
    @GameTest(template = GameTestSupport.TEMPLATE)
    public static void capabilitySimulationDoesNotMutateAnyStack(GameTestHelper helper) {
        AutomationChecks.singlesSimulationDoesNotMutateAnyStack(
                helper, GameTestSupport::automation);
    }

    /** See {@link SynchronizationChecks#clientInventoryUpdatesRejectMalformedDataAtomically}. */
    @GameTest(template = GameTestSupport.TEMPLATE)
    public static void clientInventoryUpdatesRejectMalformedDataAtomically(GameTestHelper helper) {
        SynchronizationChecks.clientInventoryUpdatesRejectMalformedDataAtomically(helper);
    }

    /** See {@link SynchronizationChecks#clientSinglesUpdatesValidateRotationBeforeInventory}. */
    @GameTest(template = GameTestSupport.TEMPLATE)
    public static void clientSinglesUpdatesValidateRotationBeforeInventory(GameTestHelper helper) {
        SynchronizationChecks.clientSinglesUpdatesValidateRotationBeforeInventory(helper);
    }

    /** See {@link CapabilityAndPersistenceChecks#releasedSinglesFormatLoads}. */
    @GameTest(template = GameTestSupport.TEMPLATE)
    public static void releasedSinglesFormatLoads(GameTestHelper helper) {
        CapabilityAndPersistenceChecks.releasedSinglesFormatLoads(helper);
    }

    /** See {@link CapabilityAndPersistenceChecks#releasedBarFormatLoads}. */
    @GameTest(template = GameTestSupport.TEMPLATE)
    public static void releasedBarFormatLoads(GameTestHelper helper) {
        CapabilityAndPersistenceChecks.releasedBarFormatLoads(helper);
    }

    /** See {@link CapabilityAndPersistenceChecks#updateTagOmitsContainerContentsAndBookPages}. */
    @GameTest(template = GameTestSupport.TEMPLATE)
    public static void updateTagOmitsContainerContentsAndBookPages(GameTestHelper helper) {
        CapabilityAndPersistenceChecks.updateTagOmitsContainerContentsAndBookPages(helper);
    }
}
