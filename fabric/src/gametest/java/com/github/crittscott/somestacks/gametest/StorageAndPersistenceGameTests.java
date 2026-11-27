package com.github.crittscott.somestacks.gametest;

import static com.github.crittscott.somestacks.gametest.GameTestScaffold.ORIGIN;
import static com.github.crittscott.somestacks.gametest.GameTestScaffold.check;
import static com.github.crittscott.somestacks.gametest.GameTestScaffold.placeBar;
import static com.github.crittscott.somestacks.gametest.GameTestScaffold.placeSingles;
import static com.github.crittscott.somestacks.gametest.GameTestScaffold.placeStorage;

import com.github.crittscott.somestacks.block.BarStackBE;
import com.github.crittscott.somestacks.block.SinglesStackBE;
import com.github.crittscott.somestacks.block.StorageStackBE;

import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.fabricmc.fabric.api.transfer.v1.item.ItemStorage;
import net.fabricmc.fabric.api.transfer.v1.item.ItemVariant;
import net.fabricmc.fabric.api.transfer.v1.storage.Storage;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;

/**
 * The automation surface and the saved state behind it: the whole-run storage exposed on every
 * side, a run advertising its reachable headroom, and each type's update tag round-tripping the
 * contents and presentation state it is responsible for.
 */
public final class StorageAndPersistenceGameTests implements FabricGameTest {
    /**
     * To reproduce in-game: connect sided item automation to each face of every stack type and
     * verify every face exposes the same inventory.
     */
    @GameTest(template = FabricGameTestSupport.TEMPLATE)
    public void itemStorageIsAvailableFromEverySide(GameTestHelper helper) {
        StorageStackBE storage = placeStorage(helper, ORIGIN);
        SinglesStackBE singles = placeSingles(helper, ORIGIN.east(3));
        BarStackBE bar = placeBar(helper, ORIGIN.east(6));

        for (Direction side : Direction.values()) {
            check(sidedStorage(storage, side) != null, "Storage storage missing on " + side);
            check(sidedStorage(singles, side) != null, "Singles storage missing on " + side);
            check(sidedStorage(bar, side) != null, "Bar storage missing on " + side);
        }
        check(sidedStorage(storage, null) != null, "Storage storage missing on null side");
        check(sidedStorage(singles, null) != null, "Singles storage missing on null side");
        check(sidedStorage(bar, null) != null, "Bar storage missing on null side");
        helper.succeed();
    }

    private static Storage<ItemVariant> sidedStorage(BlockEntity blockEntity, Direction side) {
        return ItemStorage.SIDED.find(
                (ServerLevel) blockEntity.getLevel(),
                blockEntity.getBlockPos(),
                blockEntity.getBlockState(),
                blockEntity,
                side);
    }

    /** See {@link AutomationChecks#singleBlocksAdvertiseConfiguredHeadroom}. */
    @GameTest(template = FabricGameTestSupport.TEMPLATE)
    public void singleBlockStorageAdvertisesConfiguredHeadroom(GameTestHelper helper) {
        AutomationChecks.singleBlocksAdvertiseConfiguredHeadroom(
                helper, FabricGameTestSupport::automation);
    }

    /**
     * See {@link
     * CapabilityAndPersistenceChecks#storageDiskSaveRoundTripsItemsRotationAndPermanence}.
     */
    @GameTest(template = FabricGameTestSupport.TEMPLATE)
    public void storageDiskSaveRoundTripsItemsRotationAndPermanence(GameTestHelper helper) {
        CapabilityAndPersistenceChecks.storageDiskSaveRoundTripsItemsRotationAndPermanence(helper);
    }

    /**
     * See {@link CapabilityAndPersistenceChecks#singlesDiskSaveRoundTripsItemsAndBothRotations}.
     */
    @GameTest(template = FabricGameTestSupport.TEMPLATE)
    public void singlesDiskSaveRoundTripsItemsAndBothRotations(GameTestHelper helper) {
        CapabilityAndPersistenceChecks.singlesDiskSaveRoundTripsItemsAndBothRotations(helper);
    }

    /** See {@link CapabilityAndPersistenceChecks#barDiskSaveRoundTripsItems}. */
    @GameTest(template = FabricGameTestSupport.TEMPLATE)
    public void barDiskSaveRoundTripsItems(GameTestHelper helper) {
        CapabilityAndPersistenceChecks.barDiskSaveRoundTripsItems(helper);
    }

    /**
     * See {@link CapabilityAndPersistenceChecks#updateTagsCarryRenderStateAndOmitServerOnlyData}.
     */
    @GameTest(template = FabricGameTestSupport.TEMPLATE)
    public void updateTagsCarryRenderStateAndOmitServerOnlyData(GameTestHelper helper) {
        CapabilityAndPersistenceChecks.updateTagsCarryRenderStateAndOmitServerOnlyData(helper);
    }

    /** See {@link CapabilityAndPersistenceChecks#unreadableSavedItemsAreKeptAsideOnDisk}. */
    @GameTest(template = FabricGameTestSupport.TEMPLATE)
    public void unreadableSavedItemsAreKeptAsideOnDisk(GameTestHelper helper) {
        CapabilityAndPersistenceChecks.unreadableSavedItemsAreKeptAsideOnDisk(helper);
    }

    /** See {@link CapabilityAndPersistenceChecks#setAsideItemsRecoverIntoFreeSlots}. */
    @GameTest(template = FabricGameTestSupport.TEMPLATE)
    public void setAsideItemsRecoverIntoFreeSlots(GameTestHelper helper) {
        CapabilityAndPersistenceChecks.setAsideItemsRecoverIntoFreeSlots(helper);
    }

    /** See {@link CapabilityAndPersistenceChecks#unversionedSaveUpgradesItemsOnLoad}. */
    @GameTest(template = FabricGameTestSupport.TEMPLATE)
    public void unversionedSaveUpgradesItemsOnLoad(GameTestHelper helper) {
        CapabilityAndPersistenceChecks.unversionedSaveUpgradesItemsOnLoad(helper);
    }

    /** See {@link CapabilityAndPersistenceChecks#migrationDefersUntilChunkAttachment}. */
    @GameTest(template = FabricGameTestSupport.TEMPLATE)
    public void migrationDefersUntilChunkAttachment(GameTestHelper helper) {
        CapabilityAndPersistenceChecks.migrationDefersUntilChunkAttachment(helper);
    }

    /**
     * See {@link CapabilityAndPersistenceChecks#structureRotationTransformsRotatableStackFacing}.
     */
    @GameTest(template = FabricGameTestSupport.TEMPLATE)
    public void structureRotationTransformsRotatableStackFacing(GameTestHelper helper) {
        CapabilityAndPersistenceChecks.structureRotationTransformsRotatableStackFacing(helper);
    }

    /** See {@link CapabilityAndPersistenceChecks#cachedShapesInvalidateWhenContentsChange}. */
    @GameTest(template = FabricGameTestSupport.TEMPLATE)
    public void cachedShapesInvalidateWhenContentsChange(GameTestHelper helper) {
        CapabilityAndPersistenceChecks.cachedShapesInvalidateWhenContentsChange(
                helper, FabricGameTestSupport.playerFactory(helper));
    }

    /** See {@link AutomationChecks#reentrantStructuralEditIsRefused}. */
    @GameTest(template = FabricGameTestSupport.TEMPLATE)
    public void reentrantStructuralEditIsRefused(GameTestHelper helper) {
        AutomationChecks.reentrantStructuralEditIsRefused(
                helper, FabricGameTestSupport::automation);
    }

    /** See {@link AutomationChecks#singlesSimulationDoesNotMutateAnyStack}. */
    @GameTest(template = FabricGameTestSupport.TEMPLATE)
    public void simulatedInsertionDoesNotMutateAnyStack(GameTestHelper helper) {
        AutomationChecks.singlesSimulationDoesNotMutateAnyStack(
                helper, FabricGameTestSupport::automation);
    }

    /** See {@link SynchronizationChecks#clientInventoryUpdatesRejectMalformedDataAtomically}. */
    @GameTest(template = FabricGameTestSupport.TEMPLATE)
    public void clientInventoryUpdatesRejectMalformedDataAtomically(GameTestHelper helper) {
        SynchronizationChecks.clientInventoryUpdatesRejectMalformedDataAtomically(helper);
    }

    /** See {@link SynchronizationChecks#clientSinglesUpdatesValidateRotationBeforeInventory}. */
    @GameTest(template = FabricGameTestSupport.TEMPLATE)
    public void clientSinglesUpdatesValidateRotationBeforeInventory(GameTestHelper helper) {
        SynchronizationChecks.clientSinglesUpdatesValidateRotationBeforeInventory(helper);
    }

    /** See {@link CapabilityAndPersistenceChecks#legacyNbtAndComponentItemsMigrateTogether}. */
    @GameTest(template = FabricGameTestSupport.TEMPLATE)
    public void legacyNbtAndComponentItemsMigrateTogether(GameTestHelper helper) {
        CapabilityAndPersistenceChecks.legacyNbtAndComponentItemsMigrateTogether(helper);
    }

    /** See {@link CapabilityAndPersistenceChecks#releasedSinglesFormatLoads}. */
    @GameTest(template = FabricGameTestSupport.TEMPLATE)
    public void releasedSinglesFormatLoads(GameTestHelper helper) {
        CapabilityAndPersistenceChecks.releasedSinglesFormatLoads(helper);
    }

    /** See {@link CapabilityAndPersistenceChecks#releasedBarFormatLoads}. */
    @GameTest(template = FabricGameTestSupport.TEMPLATE)
    public void releasedBarFormatLoads(GameTestHelper helper) {
        CapabilityAndPersistenceChecks.releasedBarFormatLoads(helper);
    }

    /** See {@link CapabilityAndPersistenceChecks#updateTagOmitsContainerContentsAndBookPages}. */
    @GameTest(template = FabricGameTestSupport.TEMPLATE)
    public void updateTagOmitsContainerContentsAndBookPages(GameTestHelper helper) {
        CapabilityAndPersistenceChecks.updateTagOmitsContainerContentsAndBookPages(helper);
    }
}
