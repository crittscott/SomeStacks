package com.github.crittscott.somestacks.gametest;

import com.github.crittscott.somestacks.block.BarColumn;
import com.github.crittscott.somestacks.block.BarStackBE;
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
public final class CapabilityAndPersistenceGameTests implements FabricGameTest {
    /**
     * Internal Fabric lookup check: query all six sides and the unsided storage of each stack type;
     * every lookup must expose item storage.
     *
     * <p>To check manually, place each type with one admissible item and attach a Transfer API
     * machine to each face in turn. Each attachment can access contents. The unsided lookup is
     * internal.
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
     * Internal Fabric slot-count check: single Storage, Singles, and Bar blocks advertise two
     * blocks of slots when maximum height exceeds one, otherwise one. This does not commit growth.
     *
     * <p>Nearest manual check: inspect slot counts with Transfer API automation at max_pile_height
     * above one and then at one. Growth itself is covered by the growth tests.
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
                FabricGameTestSupport.capability(storage).getSlotCount(),
                "Storage advertised slots");
        checkEquals(SinglesStackBE.SLOTS * singlesLevels,
                FabricGameTestSupport.capability(singles).getSlotCount(),
                "Singles advertised slots");
        checkEquals(BarStackBE.SLOTS * barLevels,
                FabricGameTestSupport.capability(bar).getSlotCount(),
                "Bar advertised slots");
        helper.succeed();
    }

    /**
     * Loader entry point for {@link
     * CapabilityAndPersistenceChecks#storageUpdateTagRoundTripsItemsRotationAndPermanence}; see
     * that method for
     * the scenario, manual check, and any internal-only assertions.
     */
    @GameTest(template = FabricGameTestSupport.TEMPLATE)
    public void storageUpdateTagRoundTripsItemsRotationAndPermanence(GameTestHelper helper) {
        CapabilityAndPersistenceChecks.storageUpdateTagRoundTripsItemsRotationAndPermanence(helper);
    }

    /**
     * Loader entry point for {@link
     * CapabilityAndPersistenceChecks#singlesUpdateTagRoundTripsItemsAndBothRotations}; see that
     * method for
     * the scenario, manual check, and any internal-only assertions.
     */
    @GameTest(template = FabricGameTestSupport.TEMPLATE)
    public void singlesUpdateTagRoundTripsItemsAndBothRotations(GameTestHelper helper) {
        CapabilityAndPersistenceChecks.singlesUpdateTagRoundTripsItemsAndBothRotations(helper);
    }

    /**
     * Loader entry point for {@link CapabilityAndPersistenceChecks#barUpdateTagRoundTripsItems};
     * see that method for
     * the scenario, manual check, and any internal-only assertions.
     */
    @GameTest(template = FabricGameTestSupport.TEMPLATE)
    public void barUpdateTagRoundTripsItems(GameTestHelper helper) {
        CapabilityAndPersistenceChecks.barUpdateTagRoundTripsItems(helper);
    }

    /**
     * Loader entry point for {@link
     * CapabilityAndPersistenceChecks#cachedShapesInvalidateWhenContentsChange}; see that method for
     * the scenario, manual check, and any internal-only assertions.
     */
    @GameTest(template = FabricGameTestSupport.TEMPLATE)
    public void cachedShapesInvalidateWhenContentsChange(GameTestHelper helper) {
        CapabilityAndPersistenceChecks.cachedShapesInvalidateWhenContentsChange(helper);
    }

    /**
     * Internal Fabric transaction-abort check, exercising Singles only: offer five apples at
     * grounded slot 0 through the simulation helper. The returned remainder is four, while abort
     * leaves the input and storage unchanged.
     *
     * <p>There is no player gesture for an aborted Transfer API transaction; use a machine or test
     * probe that offers then aborts without committing.
     */
    @GameTest(template = FabricGameTestSupport.TEMPLATE)
    public void simulatedInsertionDoesNotMutateAnyStack(GameTestHelper helper) {
        SinglesStackBE singles = placeSingles(helper, ORIGIN);
        SlottedStorage<ItemVariant> storage = FabricGameTestSupport.capability(singles);
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

    /**
     * Loader entry point for {@link
     * StackFoundationChecks#nestedBatchesPublishOnlyAfterTheOuterBatch}; see that method for
     * the scenario, manual check, and any internal-only assertions.
     */
    @GameTest(template = FabricGameTestSupport.TEMPLATE)
    public void nestedBatchesPublishOnlyAfterTheOuterBatch(GameTestHelper helper) {
        StackFoundationChecks.nestedBatchesPublishOnlyAfterTheOuterBatch(helper);
    }

    /**
     * Loader entry point for {@link
     * StackFoundationChecks#structuralEditsInvalidateRunBoundsWithinTheTick}; see that method for
     * the scenario, manual check, and any internal-only assertions.
     */
    @GameTest(template = FabricGameTestSupport.TEMPLATE)
    public void structuralEditsInvalidateRunBoundsWithinTheTick(GameTestHelper helper) {
        StackFoundationChecks.structuralEditsInvalidateRunBoundsWithinTheTick(helper);
    }

    /**
     * Loader entry point for {@link
     * StackFoundationChecks#loadedContentsRefreshComparatorFillAndShapes}; see that method for
     * the scenario, manual check, and any internal-only assertions.
     */
    @GameTest(template = FabricGameTestSupport.TEMPLATE)
    public void loadedContentsRefreshComparatorFillAndShapes(GameTestHelper helper) {
        StackFoundationChecks.loadedContentsRefreshComparatorFillAndShapes(helper);
    }

    /**
     * Loader entry point for {@link
     * StackFoundationChecks#runContractPreservesTypeSpecificMutations}; see that method for
     * the scenario, manual check, and any internal-only assertions.
     */
    @GameTest(template = FabricGameTestSupport.TEMPLATE)
    public void runContractPreservesTypeSpecificMutations(GameTestHelper helper) {
        StackFoundationChecks.runContractPreservesTypeSpecificMutations(helper);
    }

    /**
     * Loader entry point for {@link ServiceAndConfigChecks#installedServicesAndLogoutResetPacing};
     * see that method for
     * the scenario, manual check, and any internal-only assertions.
     */
    @GameTest(template = FabricGameTestSupport.TEMPLATE)
    public void installedServicesAndLogoutResetPacing(GameTestHelper helper) {
        ServiceAndConfigChecks.installedServicesAndLogoutResetPacing(helper, net.fabricmc.fabric.api.entity.FakePlayer.get(helper.getLevel()));
    }

    /**
     * Loader entry point for {@link ServiceAndConfigChecks#listEditsPublishImmutablePolicy}; see
     * that method for
     * the scenario, manual check, and any internal-only assertions.
     */
    @GameTest(template = FabricGameTestSupport.TEMPLATE)
    public void listEditsPublishImmutablePolicy(GameTestHelper helper) {
        ServiceAndConfigChecks.listEditsPublishImmutablePolicy(helper);
    }
}
