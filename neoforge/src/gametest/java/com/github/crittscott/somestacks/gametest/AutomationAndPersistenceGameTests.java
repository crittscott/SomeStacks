package com.github.crittscott.somestacks.gametest;

import com.github.crittscott.somestacks.ServerConfig;
import com.github.crittscott.somestacks.block.BarStackBE;
import com.github.crittscott.somestacks.block.RunEdit;
import com.github.crittscott.somestacks.block.SinglesStackBE;
import com.github.crittscott.somestacks.block.StorageStackBE;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.item.ItemResource;

import static com.github.crittscott.somestacks.gametest.GameTestScaffold.ORIGIN;
import static com.github.crittscott.somestacks.gametest.GameTestScaffold.check;
import static com.github.crittscott.somestacks.gametest.GameTestScaffold.checkEquals;
import static com.github.crittscott.somestacks.gametest.GameTestScaffold.placeBar;
import static com.github.crittscott.somestacks.gametest.GameTestScaffold.placeSingles;
import static com.github.crittscott.somestacks.gametest.GameTestScaffold.placeStorage;
import static com.github.crittscott.somestacks.gametest.GameTestSupport.count;

/**
 * The automation surface and the saved state behind it: the item handler exposed on every side,
 * a run advertising its reachable headroom, and each type's update tag round-tripping the contents
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
            check(GameTestSupport.capabilityPresent(storage, side),
                    "Storage capability missing on " + side);
            check(GameTestSupport.capabilityPresent(singles, side),
                    "Singles capability missing on " + side);
            check(GameTestSupport.capabilityPresent(bar, side),
                    "Bar capability missing on " + side);
        }
        check(GameTestSupport.capabilityPresent(storage, null),
                "Storage capability missing on null side");
        check(GameTestSupport.capabilityPresent(singles, null),
                "Singles capability missing on null side");
        check(GameTestSupport.capabilityPresent(bar, null),
                "Bar capability missing on null side");
        helper.succeed();
    }

    /**
     * To reproduce in-game: connect item automation to a one-block stack below its height limit and
     * verify it can address one additional block of empty positions for growth.
     */
    public static void singleBlockCapabilitiesAdvertiseConfiguredHeadroom(
            GameTestHelper helper) {
        StorageStackBE storage = placeStorage(helper, ORIGIN);
        SinglesStackBE singles = placeSingles(helper, ORIGIN.east(3));
        BarStackBE bar = placeBar(helper, ORIGIN.east(6));

        int storageLevels = ServerConfig.maxPileHeight() > 1 ? 2 : 1;
        int singlesLevels = ServerConfig.maxPileHeight() > 1 ? 2 : 1;
        int barLevels = ServerConfig.maxPileHeight() > 1 ? 2 : 1;
        checkEquals(StorageStackBE.SLOTS * storageLevels,
                GameTestSupport.capability(storage).size(),
                "Storage advertised slots");
        checkEquals(SinglesStackBE.SLOTS * singlesLevels,
                GameTestSupport.capability(singles).size(),
                "Singles advertised slots");
        checkEquals(BarStackBE.SLOTS * barLevels,
                GameTestSupport.capability(bar).size(),
                "Bar advertised slots");
        helper.succeed();
    }

    /** See {@link AutomationAndPersistenceChecks#storageDiskSaveRoundTripsItemsRotationAndPermanence}. */
    public static void storageDiskSaveRoundTripsItemsRotationAndPermanence(
            GameTestHelper helper) {
        AutomationAndPersistenceChecks.storageDiskSaveRoundTripsItemsRotationAndPermanence(helper);
    }

    /** See {@link AutomationAndPersistenceChecks#singlesDiskSaveRoundTripsItemsAndBothRotations}. */
    public static void singlesDiskSaveRoundTripsItemsAndBothRotations(
            GameTestHelper helper) {
        AutomationAndPersistenceChecks.singlesDiskSaveRoundTripsItemsAndBothRotations(helper);
    }

    /** See {@link AutomationAndPersistenceChecks#barDiskSaveRoundTripsItems}. */
    public static void barDiskSaveRoundTripsItems(GameTestHelper helper) {
        AutomationAndPersistenceChecks.barDiskSaveRoundTripsItems(helper);
    }

    /** See {@link AutomationAndPersistenceChecks#updateTagsCarryRenderStateAndOmitServerOnlyData}. */
    public static void updateTagsCarryRenderStateAndOmitServerOnlyData(GameTestHelper helper) {
        AutomationAndPersistenceChecks.updateTagsCarryRenderStateAndOmitServerOnlyData(helper);
    }

    /** See {@link AutomationAndPersistenceChecks#unreadableSavedItemsAreKeptAsideOnDisk}. */
    public static void unreadableSavedItemsAreKeptAsideOnDisk(GameTestHelper helper) {
        AutomationAndPersistenceChecks.unreadableSavedItemsAreKeptAsideOnDisk(helper);
    }

    /** See {@link AutomationAndPersistenceChecks#setAsideItemsRecoverIntoFreeSlots}. */
    public static void setAsideItemsRecoverIntoFreeSlots(GameTestHelper helper) {
        AutomationAndPersistenceChecks.setAsideItemsRecoverIntoFreeSlots(helper);
    }

    /** See {@link AutomationAndPersistenceChecks#previousReleaseSaveUpgradesOnLoad}. */
    public static void previousReleaseSaveUpgradesOnLoad(GameTestHelper helper) {
        AutomationAndPersistenceChecks.previousReleaseSaveUpgradesOnLoad(helper);
    }

    /** See {@link AutomationAndPersistenceChecks#structureRotationTransformsRotatableStackFacing}. */
    public static void structureRotationTransformsRotatableStackFacing(GameTestHelper helper) {
        AutomationAndPersistenceChecks.structureRotationTransformsRotatableStackFacing(helper);
    }

    /** See {@link AutomationAndPersistenceChecks#cachedShapesInvalidateWhenContentsChange}. */
    public static void cachedShapesInvalidateWhenContentsChange(GameTestHelper helper) {
        AutomationAndPersistenceChecks.cachedShapesInvalidateWhenContentsChange(
                helper, GameTestSupport.playerFactory(helper));
    }

    /** No in-game reproduction applies: a reentrant NeoForge capability edit is refused. */
    public static void reentrantStructuralEditIsRefused(GameTestHelper helper) {
        SinglesStackBE singles = placeSingles(helper, ORIGIN);
        ResourceHandler<ItemResource> capability = GameTestSupport.capability(singles);
        ItemStack offered = new ItemStack(Items.STICK);
        boolean claimed = RunEdit.begin();
        try {
            check(claimed, "Structural edit guard was already claimed");
            checkEquals(0, GameTestSupport.offer(capability, 0, offered, true),
                    "Reentrant insertion accepted an item");
        } finally {
            if (claimed) {
                RunEdit.end();
            }
        }
        checkEquals(1, GameTestSupport.offer(capability, 0, offered, true),
                "Released guard still refused insertion");
        helper.succeed();
    }

    /**
     * No in-game reproduction applies: this verifies that simulated loader capability insertion and
     * extraction leave Storage, Singles, and Bar stacks unchanged.
     */
    public static void capabilitySimulationDoesNotMutateAnyStack(GameTestHelper helper) {
        SinglesStackBE singles = placeSingles(helper, ORIGIN);
        ResourceHandler<ItemResource> capability = GameTestSupport.capability(singles);
        ItemStack offered = new ItemStack(Items.APPLE, 5);

        // Cell 0 is a bottom-layer cell of a column standing on the world, so it is grounded
        // outright and the simulation has something to accept.
        int accepted = GameTestSupport.offer(capability, 0, offered, false);

        checkEquals(5, offered.getCount(), "Simulation mutated its input");
        checkEquals(0, count(capability, Items.APPLE),
                "Simulation mutated the column");
        checkEquals(1, accepted,
                "A cell takes one item, so one call should accept one");
        helper.succeed();
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
