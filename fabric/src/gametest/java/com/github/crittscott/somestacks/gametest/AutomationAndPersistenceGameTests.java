package com.github.crittscott.somestacks.gametest;

import static com.github.crittscott.somestacks.gametest.GameTestScaffold.ORIGIN;
import static com.github.crittscott.somestacks.gametest.GameTestScaffold.check;
import static com.github.crittscott.somestacks.gametest.GameTestScaffold.placeBar;
import static com.github.crittscott.somestacks.gametest.GameTestScaffold.placeSingles;
import static com.github.crittscott.somestacks.gametest.GameTestScaffold.placeStorage;

import com.github.crittscott.somestacks.block.BarStackBE;
import com.github.crittscott.somestacks.block.SinglesStackBE;
import com.github.crittscott.somestacks.block.StorageStackBE;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.fabricmc.fabric.api.transfer.v1.item.ItemStorage;
import net.fabricmc.fabric.api.transfer.v1.item.ItemVariant;
import net.fabricmc.fabric.api.transfer.v1.storage.Storage;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;

/**
 * The automation surface and the saved state behind it: the whole-run storage exposed on every
 * side, a run advertising its reachable headroom, and each type's update tag round-tripping the
 * contents and presentation state it is responsible for.
 */
public final class AutomationAndPersistenceGameTests {
    /**
     * To reproduce in-game: connect sided item automation to each face of every stack type and
     * verify every face exposes the same inventory.
     */
    @GameTest(structure = FabricGameTestSupport.TEMPLATE, maxTicks = 100)
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
    @GameTest(structure = FabricGameTestSupport.TEMPLATE, maxTicks = 100)
    public void singleBlockStorageAdvertisesConfiguredHeadroom(GameTestHelper helper) {
        AutomationChecks.singleBlocksAdvertiseConfiguredHeadroom(
                helper, FabricGameTestSupport::automation);
    }

    /**
     * See {@link
     * AutomationAndPersistenceChecks#storageDiskSaveRoundTripsItemsRotationAndPermanence}.
     */
    @GameTest(structure = FabricGameTestSupport.TEMPLATE, maxTicks = 100)
    public void storageDiskSaveRoundTripsItemsRotationAndPermanence(GameTestHelper helper) {
        AutomationAndPersistenceChecks.storageDiskSaveRoundTripsItemsRotationAndPermanence(helper);
    }

    /**
     * See {@link AutomationAndPersistenceChecks#singlesDiskSaveRoundTripsItemsAndBothRotations}.
     */
    @GameTest(structure = FabricGameTestSupport.TEMPLATE, maxTicks = 100)
    public void singlesDiskSaveRoundTripsItemsAndBothRotations(GameTestHelper helper) {
        AutomationAndPersistenceChecks.singlesDiskSaveRoundTripsItemsAndBothRotations(helper);
    }

    /** See {@link AutomationAndPersistenceChecks#barDiskSaveRoundTripsItems}. */
    @GameTest(structure = FabricGameTestSupport.TEMPLATE, maxTicks = 100)
    public void barDiskSaveRoundTripsItems(GameTestHelper helper) {
        AutomationAndPersistenceChecks.barDiskSaveRoundTripsItems(helper);
    }

    /**
     * See {@link AutomationAndPersistenceChecks#updateTagsCarryRenderStateAndOmitServerOnlyData}.
     */
    @GameTest(structure = FabricGameTestSupport.TEMPLATE, maxTicks = 100)
    public void updateTagsCarryRenderStateAndOmitServerOnlyData(GameTestHelper helper) {
        AutomationAndPersistenceChecks.updateTagsCarryRenderStateAndOmitServerOnlyData(helper);
    }

    /** See {@link AutomationAndPersistenceChecks#updateTagOmitsContainerContentsAndBookPages}. */
    @GameTest(structure = FabricGameTestSupport.TEMPLATE, maxTicks = 100)
    public void updateTagOmitsContainerContentsAndBookPages(GameTestHelper helper) {
        AutomationAndPersistenceChecks.updateTagOmitsContainerContentsAndBookPages(helper);
    }

    /** See {@link AutomationAndPersistenceChecks#unreadableSavedItemsAreKeptAsideOnDisk}. */
    @GameTest(structure = FabricGameTestSupport.TEMPLATE, maxTicks = 100)
    public void unreadableSavedItemsAreKeptAsideOnDisk(GameTestHelper helper) {
        AutomationAndPersistenceChecks.unreadableSavedItemsAreKeptAsideOnDisk(helper);
    }

    /** See {@link AutomationAndPersistenceChecks#setAsideItemsRecoverIntoFreeSlots}. */
    @GameTest(structure = FabricGameTestSupport.TEMPLATE, maxTicks = 100)
    public void setAsideItemsRecoverIntoFreeSlots(GameTestHelper helper) {
        AutomationAndPersistenceChecks.setAsideItemsRecoverIntoFreeSlots(helper);
    }

    /** See {@link AutomationAndPersistenceChecks#previousReleaseSaveUpgradesOnLoad}. */
    @GameTest(structure = FabricGameTestSupport.TEMPLATE, maxTicks = 100)
    public void previousReleaseSaveUpgradesOnLoad(GameTestHelper helper) {
        AutomationAndPersistenceChecks.previousReleaseSaveUpgradesOnLoad(helper);
    }

    /** See {@link AutomationAndPersistenceChecks#releasedSinglesFormatLoads}. */
    @GameTest(structure = FabricGameTestSupport.TEMPLATE, maxTicks = 100)
    public void releasedSinglesFormatLoads(GameTestHelper helper) {
        AutomationAndPersistenceChecks.releasedSinglesFormatLoads(helper);
    }

    /** See {@link AutomationAndPersistenceChecks#releasedBarFormatLoads}. */
    @GameTest(structure = FabricGameTestSupport.TEMPLATE, maxTicks = 100)
    public void releasedBarFormatLoads(GameTestHelper helper) {
        AutomationAndPersistenceChecks.releasedBarFormatLoads(helper);
    }

    /**
     * See {@link AutomationAndPersistenceChecks#structureRotationTransformsRotatableStackFacing}.
     */
    @GameTest(structure = FabricGameTestSupport.TEMPLATE, maxTicks = 100)
    public void structureRotationTransformsRotatableStackFacing(GameTestHelper helper) {
        AutomationAndPersistenceChecks.structureRotationTransformsRotatableStackFacing(helper);
    }

    /** See {@link AutomationAndPersistenceChecks#cachedShapesInvalidateWhenContentsChange}. */
    @GameTest(structure = FabricGameTestSupport.TEMPLATE, maxTicks = 100)
    public void cachedShapesInvalidateWhenContentsChange(GameTestHelper helper) {
        AutomationAndPersistenceChecks.cachedShapesInvalidateWhenContentsChange(
                helper, FabricGameTestSupport.playerFactory(helper));
    }

    /** See {@link AutomationChecks#reentrantStructuralEditIsRefused}. */
    @GameTest(structure = FabricGameTestSupport.TEMPLATE, maxTicks = 100)
    public void reentrantStructuralEditIsRefused(GameTestHelper helper) {
        AutomationChecks.reentrantStructuralEditIsRefused(
                helper, FabricGameTestSupport::automation);
    }

    /** See {@link AutomationChecks#singlesSimulationDoesNotMutateAnyStack}. */
    @GameTest(structure = FabricGameTestSupport.TEMPLATE, maxTicks = 100)
    public void simulatedInsertionDoesNotMutateAnyStack(GameTestHelper helper) {
        AutomationChecks.singlesSimulationDoesNotMutateAnyStack(
                helper, FabricGameTestSupport::automation);
    }

    /** See {@link SynchronizationChecks#clientInventoryUpdatesRejectMalformedDataAtomically}. */
    @GameTest(structure = FabricGameTestSupport.TEMPLATE, maxTicks = 100)
    public void clientInventoryUpdatesRejectMalformedDataAtomically(GameTestHelper helper) {
        SynchronizationChecks.clientInventoryUpdatesRejectMalformedDataAtomically(helper);
    }

    /** See {@link SynchronizationChecks#clientSinglesUpdatesValidateRotationBeforeInventory}. */
    @GameTest(structure = FabricGameTestSupport.TEMPLATE, maxTicks = 100)
    public void clientSinglesUpdatesValidateRotationBeforeInventory(GameTestHelper helper) {
        SynchronizationChecks.clientSinglesUpdatesValidateRotationBeforeInventory(helper);
    }
}
