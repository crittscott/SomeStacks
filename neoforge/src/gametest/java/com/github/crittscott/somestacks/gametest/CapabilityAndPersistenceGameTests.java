package com.github.crittscott.somestacks.gametest;

import com.github.crittscott.somestacks.SomeStacksNeoForge;
import com.github.crittscott.somestacks.block.BarColumn;
import com.github.crittscott.somestacks.block.BarStackBE;
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

    @GameTest(template = GameTestSupport.TEMPLATE)
    public static void storageUpdateTagRoundTripsItemsRotationAndPermanence(
            GameTestHelper helper) {
        CapabilityAndPersistenceChecks.storageUpdateTagRoundTripsItemsRotationAndPermanence(helper);
    }

    @GameTest(template = GameTestSupport.TEMPLATE)
    public static void singlesUpdateTagRoundTripsItemsAndBothRotations(
            GameTestHelper helper) {
        CapabilityAndPersistenceChecks.singlesUpdateTagRoundTripsItemsAndBothRotations(helper);
    }

    @GameTest(template = GameTestSupport.TEMPLATE)
    public static void barUpdateTagRoundTripsItems(GameTestHelper helper) {
        CapabilityAndPersistenceChecks.barUpdateTagRoundTripsItems(helper);
    }

    @GameTest(template = GameTestSupport.TEMPLATE)
    public static void cachedShapesInvalidateWhenContentsChange(GameTestHelper helper) {
        CapabilityAndPersistenceChecks.cachedShapesInvalidateWhenContentsChange(helper);
    }

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
