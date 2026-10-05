package com.github.crittscott.somestacks.gametest;

import com.github.crittscott.somestacks.block.BarColumn;
import com.github.crittscott.somestacks.block.BarStackBE;
import com.github.crittscott.somestacks.block.RunEdit;
import com.github.crittscott.somestacks.block.SinglesColumn;
import com.github.crittscott.somestacks.block.SinglesStackBE;
import com.github.crittscott.somestacks.block.StoragePile;
import com.github.crittscott.somestacks.block.StorageStackBE;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.fabricmc.fabric.api.transfer.v1.item.ItemStorage;
import net.fabricmc.fabric.api.transfer.v1.item.ItemVariant;
import net.fabricmc.fabric.api.transfer.v1.storage.SlottedStorage;
import net.fabricmc.fabric.api.transfer.v1.storage.Storage;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.entity.BlockEntity;

import static com.github.crittscott.somestacks.gametest.GameTestScaffold.ORIGIN;
import static com.github.crittscott.somestacks.gametest.GameTestScaffold.check;
import static com.github.crittscott.somestacks.gametest.GameTestScaffold.checkEquals;
import static com.github.crittscott.somestacks.gametest.GameTestScaffold.placeBar;
import static com.github.crittscott.somestacks.gametest.GameTestScaffold.placeSingles;
import static com.github.crittscott.somestacks.gametest.GameTestScaffold.placeStorage;

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

    /**
     * To reproduce in-game: connect item automation to a one-block stack below its height limit and
     * verify it can address one additional block of empty positions for growth.
     */
    @GameTest(template = FabricGameTestSupport.TEMPLATE)
    public void singleBlockStorageAdvertisesConfiguredHeadroom(GameTestHelper helper) {
        StorageStackBE storage = placeStorage(helper, ORIGIN);
        SinglesStackBE singles = placeSingles(helper, ORIGIN.east(3));
        BarStackBE bar = placeBar(helper, ORIGIN.east(6));

        int storageLevels = StoragePile.maxHeight() > 1 ? 2 : 1;
        int singlesLevels = SinglesColumn.maxHeight() > 1 ? 2 : 1;
        int barLevels = BarColumn.maxHeight() > 1 ? 2 : 1;
        checkEquals(StorageStackBE.SLOTS * storageLevels,
                FabricGameTestSupport.storage(storage).getSlotCount(),
                "Storage advertised slots");
        checkEquals(SinglesStackBE.SLOTS * singlesLevels,
                FabricGameTestSupport.storage(singles).getSlotCount(),
                "Singles advertised slots");
        checkEquals(BarStackBE.SLOTS * barLevels,
                FabricGameTestSupport.storage(bar).getSlotCount(),
                "Bar advertised slots");
        helper.succeed();
    }

    /** See {@link CapabilityAndPersistenceChecks#storageDiskSaveRoundTripsItemsRotationAndPermanence}. */
    @GameTest(template = FabricGameTestSupport.TEMPLATE)
    public void storageDiskSaveRoundTripsItemsRotationAndPermanence(GameTestHelper helper) {
        CapabilityAndPersistenceChecks.storageDiskSaveRoundTripsItemsRotationAndPermanence(helper);
    }

    /** See {@link CapabilityAndPersistenceChecks#singlesDiskSaveRoundTripsItemsAndBothRotations}. */
    @GameTest(template = FabricGameTestSupport.TEMPLATE)
    public void singlesDiskSaveRoundTripsItemsAndBothRotations(GameTestHelper helper) {
        CapabilityAndPersistenceChecks.singlesDiskSaveRoundTripsItemsAndBothRotations(helper);
    }

    /** See {@link CapabilityAndPersistenceChecks#barDiskSaveRoundTripsItems}. */
    @GameTest(template = FabricGameTestSupport.TEMPLATE)
    public void barDiskSaveRoundTripsItems(GameTestHelper helper) {
        CapabilityAndPersistenceChecks.barDiskSaveRoundTripsItems(helper);
    }

    /** See {@link CapabilityAndPersistenceChecks#updateTagsCarryClientStateAndOmitSetAside}. */
    @GameTest(template = FabricGameTestSupport.TEMPLATE)
    public void updateTagsCarryClientStateAndOmitSetAside(GameTestHelper helper) {
        CapabilityAndPersistenceChecks.updateTagsCarryClientStateAndOmitSetAside(helper);
    }

    /** See {@link CapabilityAndPersistenceChecks#unreadableSavedItemsAreKeptAsideOnDisk}. */
    @GameTest(template = FabricGameTestSupport.TEMPLATE)
    public void unreadableSavedItemsAreKeptAsideOnDisk(GameTestHelper helper) {
        CapabilityAndPersistenceChecks.unreadableSavedItemsAreKeptAsideOnDisk(helper);
    }

    /** See {@link CapabilityAndPersistenceChecks#unversionedSaveUpgradesItemsOnLoad}. */
    @GameTest(template = FabricGameTestSupport.TEMPLATE)
    public void unversionedSaveUpgradesItemsOnLoad(GameTestHelper helper) {
        CapabilityAndPersistenceChecks.unversionedSaveUpgradesItemsOnLoad(helper);
    }

    /** See {@link CapabilityAndPersistenceChecks#cachedShapesInvalidateWhenContentsChange}. */
    @GameTest(template = FabricGameTestSupport.TEMPLATE)
    public void cachedShapesInvalidateWhenContentsChange(GameTestHelper helper) {
        CapabilityAndPersistenceChecks.cachedShapesInvalidateWhenContentsChange(helper);
    }

    /** No in-game reproduction applies: a reentrant Fabric storage edit is refused. */
    @GameTest(template = FabricGameTestSupport.TEMPLATE)
    public void reentrantStructuralEditIsRefused(GameTestHelper helper) {
        SinglesStackBE singles = placeSingles(helper, ORIGIN);
        SlottedStorage<ItemVariant> storage = FabricGameTestSupport.storage(singles);
        ItemStack offered = new ItemStack(Items.STICK);
        boolean claimed = RunEdit.begin();
        try {
            check(claimed, "Structural edit guard was already claimed");
            checkEquals(1, FabricGameTestSupport.insertAt(storage, 0, offered, false).getCount(),
                    "Reentrant insertion accepted an item");
        } finally {
            if (claimed) {
                RunEdit.end();
            }
        }
        check(FabricGameTestSupport.insertAt(storage, 0, offered, false).isEmpty(),
                "Released guard still refused insertion");
        helper.succeed();
    }

    /**
     * No in-game reproduction applies: this verifies that aborted Fabric transactions leave
     * Storage, Singles, and Bar stacks unchanged.
     */
    @GameTest(template = FabricGameTestSupport.TEMPLATE)
    public void simulatedInsertionDoesNotMutateAnyStack(GameTestHelper helper) {
        SinglesStackBE singles = placeSingles(helper, ORIGIN);
        SlottedStorage<ItemVariant> storage = FabricGameTestSupport.storage(singles);
        ItemStack offered = new ItemStack(Items.APPLE, 5);

        // Cell 0 is a bottom-layer cell of a column standing on the world, so it is grounded
        // outright and the simulation has something to accept.
        ItemStack remainder = FabricGameTestSupport.insertAt(storage, 0, offered, true);

        checkEquals(5, offered.getCount(), "Simulation mutated its input");
        checkEquals(0, FabricGameTestSupport.count(storage, Items.APPLE),
                "Simulation mutated the column");
        checkEquals(4, remainder.getCount(),
                "A cell takes one item, so one call should accept one");
        helper.succeed();
    }
}
