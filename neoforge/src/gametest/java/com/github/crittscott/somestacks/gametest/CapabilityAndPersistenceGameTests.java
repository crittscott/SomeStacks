package com.github.crittscott.somestacks.gametest;

import com.github.crittscott.somestacks.SomeStacksNeoForge;
import com.github.crittscott.somestacks.block.BarColumn;
import com.github.crittscott.somestacks.block.BarStackBE;
import com.github.crittscott.somestacks.block.RunEdit;
import com.github.crittscott.somestacks.block.SinglesColumn;
import com.github.crittscott.somestacks.block.SinglesStackBE;
import com.github.crittscott.somestacks.block.StoragePile;
import com.github.crittscott.somestacks.block.StorageStackBE;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.items.IItemHandler;

import static com.github.crittscott.somestacks.gametest.GameTestScaffold.ORIGIN;
import static com.github.crittscott.somestacks.gametest.GameTestScaffold.check;
import static com.github.crittscott.somestacks.gametest.GameTestScaffold.checkEquals;
import static com.github.crittscott.somestacks.gametest.GameTestSupport.count;
import static com.github.crittscott.somestacks.gametest.GameTestScaffold.placeBar;
import static com.github.crittscott.somestacks.gametest.GameTestScaffold.placeSingles;
import static com.github.crittscott.somestacks.gametest.GameTestScaffold.placeStorage;

/**
 * The automation surface and the saved state behind it: the item handler exposed on every side,
 * a run advertising its reachable headroom, and each type's update tag round-tripping the contents
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
    @GameTest(template = GameTestSupport.TEMPLATE)
    public static void singleBlockCapabilitiesAdvertiseConfiguredHeadroom(
            GameTestHelper helper) {
        StorageStackBE storage = placeStorage(helper, ORIGIN);
        SinglesStackBE singles = placeSingles(helper, ORIGIN.east(3));
        BarStackBE bar = placeBar(helper, ORIGIN.east(6));

        int storageLevels = StoragePile.maxHeight() > 1 ? 2 : 1;
        int singlesLevels = SinglesColumn.maxHeight() > 1 ? 2 : 1;
        int barLevels = BarColumn.maxHeight() > 1 ? 2 : 1;
        checkEquals(StorageStackBE.SLOTS * storageLevels,
                GameTestSupport.capability(storage).getSlots(),
                "Storage advertised slots");
        checkEquals(SinglesStackBE.SLOTS * singlesLevels,
                GameTestSupport.capability(singles).getSlots(),
                "Singles advertised slots");
        checkEquals(BarStackBE.SLOTS * barLevels,
                GameTestSupport.capability(bar).getSlots(),
                "Bar advertised slots");
        helper.succeed();
    }

    /** See {@link CapabilityAndPersistenceChecks#storageDiskSaveRoundTripsItemsRotationAndPermanence}. */
    @GameTest(template = GameTestSupport.TEMPLATE)
    public static void storageDiskSaveRoundTripsItemsRotationAndPermanence(
            GameTestHelper helper) {
        CapabilityAndPersistenceChecks.storageDiskSaveRoundTripsItemsRotationAndPermanence(helper);
    }

    /** See {@link CapabilityAndPersistenceChecks#singlesDiskSaveRoundTripsItemsAndBothRotations}. */
    @GameTest(template = GameTestSupport.TEMPLATE)
    public static void singlesDiskSaveRoundTripsItemsAndBothRotations(
            GameTestHelper helper) {
        CapabilityAndPersistenceChecks.singlesDiskSaveRoundTripsItemsAndBothRotations(helper);
    }

    /** See {@link CapabilityAndPersistenceChecks#barDiskSaveRoundTripsItems}. */
    @GameTest(template = GameTestSupport.TEMPLATE)
    public static void barDiskSaveRoundTripsItems(GameTestHelper helper) {
        CapabilityAndPersistenceChecks.barDiskSaveRoundTripsItems(helper);
    }

    /** See {@link CapabilityAndPersistenceChecks#updateTagsCarryClientStateAndOmitSetAside}. */
    @GameTest(template = GameTestSupport.TEMPLATE)
    public static void updateTagsCarryClientStateAndOmitSetAside(GameTestHelper helper) {
        CapabilityAndPersistenceChecks.updateTagsCarryClientStateAndOmitSetAside(helper);
    }

    /** See {@link CapabilityAndPersistenceChecks#unreadableSavedItemsAreKeptAsideOnDisk}. */
    @GameTest(template = GameTestSupport.TEMPLATE)
    public static void unreadableSavedItemsAreKeptAsideOnDisk(GameTestHelper helper) {
        CapabilityAndPersistenceChecks.unreadableSavedItemsAreKeptAsideOnDisk(helper);
    }

    /** See {@link CapabilityAndPersistenceChecks#unversionedSaveUpgradesItemsOnLoad}. */
    @GameTest(template = GameTestSupport.TEMPLATE)
    public static void unversionedSaveUpgradesItemsOnLoad(GameTestHelper helper) {
        CapabilityAndPersistenceChecks.unversionedSaveUpgradesItemsOnLoad(helper);
    }

    /** See {@link CapabilityAndPersistenceChecks#cachedShapesInvalidateWhenContentsChange}. */
    @GameTest(template = GameTestSupport.TEMPLATE)
    public static void cachedShapesInvalidateWhenContentsChange(GameTestHelper helper) {
        CapabilityAndPersistenceChecks.cachedShapesInvalidateWhenContentsChange(helper);
    }

    /** No in-game reproduction applies: a reentrant NeoForge capability edit is refused. */
    @GameTest(template = GameTestSupport.TEMPLATE)
    public static void reentrantStructuralEditIsRefused(GameTestHelper helper) {
        SinglesStackBE singles = placeSingles(helper, ORIGIN);
        IItemHandler capability = GameTestSupport.capability(singles);
        ItemStack offered = new ItemStack(Items.STICK);
        boolean claimed = RunEdit.begin();
        try {
            check(claimed, "Structural edit guard was already claimed");
            checkEquals(1, capability.insertItem(0, offered, false).getCount(),
                    "Reentrant insertion accepted an item");
        } finally {
            if (claimed) {
                RunEdit.end();
            }
        }
        check(capability.insertItem(0, offered, false).isEmpty(),
                "Released guard still refused insertion");
        helper.succeed();
    }

    /**
     * No in-game reproduction applies: this verifies that simulated loader capability insertion and
     * extraction leave Storage, Singles, and Bar stacks unchanged.
     */
    @GameTest(template = GameTestSupport.TEMPLATE)
    public static void capabilitySimulationDoesNotMutateAnyStack(GameTestHelper helper) {
        SinglesStackBE singles = placeSingles(helper, ORIGIN);
        IItemHandler capability = GameTestSupport.capability(singles);
        ItemStack offered = new ItemStack(Items.APPLE, 5);

        // Cell 0 is a bottom-layer cell of a column standing on the world, so it is grounded
        // outright and the simulation has something to accept.
        ItemStack remainder = capability.insertItem(0, offered, true);

        checkEquals(5, offered.getCount(), "Simulation mutated its input");
        checkEquals(0, count(capability, Items.APPLE),
                "Simulation mutated the column");
        checkEquals(4, remainder.getCount(),
                "A cell takes one item, so one call should accept one");
        helper.succeed();
    }
}
