package com.github.crittscott.somestacks.gametest;

import com.github.crittscott.somestacks.SomeStacks;
import com.github.crittscott.somestacks.block.BarColumn;
import com.github.crittscott.somestacks.block.BarStackBE;
import com.github.crittscott.somestacks.block.SinglesColumn;
import com.github.crittscott.somestacks.block.SinglesStackBE;
import com.github.crittscott.somestacks.block.StoragePile;
import com.github.crittscott.somestacks.block.StorageStackBE;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.items.IItemHandler;

import static com.github.crittscott.somestacks.gametest.GameTestSupport.ORIGIN;
import static com.github.crittscott.somestacks.gametest.GameTestSupport.check;
import static com.github.crittscott.somestacks.gametest.GameTestSupport.checkEquals;

@GameTestHolder(SomeStacks.MODID)
@PrefixGameTestTemplate(false)
public final class CapabilityAndPersistenceGameTests {
    private CapabilityAndPersistenceGameTests() {}

    @GameTest(template = GameTestSupport.TEMPLATE)
    public static void itemCapabilityIsAvailableFromEverySide(GameTestHelper helper) {
        StorageStackBE storage = GameTestSupport.placeStorage(helper, ORIGIN);
        SinglesStackBE singles = GameTestSupport.placeSingles(helper, ORIGIN.east(3));
        BarStackBE bar = GameTestSupport.placeBar(helper, ORIGIN.east(6));

        for (Direction side : Direction.values()) {
            check(storage.getCapability(ForgeCapabilities.ITEM_HANDLER, side).isPresent(),
                    "Storage capability missing on " + side);
            check(singles.getCapability(ForgeCapabilities.ITEM_HANDLER, side).isPresent(),
                    "Singles capability missing on " + side);
            check(bar.getCapability(ForgeCapabilities.ITEM_HANDLER, side).isPresent(),
                    "Bar capability missing on " + side);
        }
        check(storage.getCapability(ForgeCapabilities.ITEM_HANDLER, null).isPresent(),
                "Storage capability missing on null side");
        check(singles.getCapability(ForgeCapabilities.ITEM_HANDLER, null).isPresent(),
                "Singles capability missing on null side");
        check(bar.getCapability(ForgeCapabilities.ITEM_HANDLER, null).isPresent(),
                "Bar capability missing on null side");
        helper.succeed();
    }

    @GameTest(template = GameTestSupport.TEMPLATE)
    public static void singleBlockCapabilitiesAdvertiseConfiguredHeadroom(
            GameTestHelper helper) {
        StorageStackBE storage = GameTestSupport.placeStorage(helper, ORIGIN);
        SinglesStackBE singles = GameTestSupport.placeSingles(helper, ORIGIN.east(3));
        BarStackBE bar = GameTestSupport.placeBar(helper, ORIGIN.east(6));

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
        StorageStackBE source = GameTestSupport.placeStorage(helper, ORIGIN);
        StorageStackBE loaded = GameTestSupport.placeStorage(helper, ORIGIN.east(3));
        CompoundTag identity = new CompoundTag();
        identity.putString("test", "storage");
        ItemStack stored = new ItemStack(Items.STONE, 23);
        stored.setTag(identity);
        source.getItems().insertItem(7, stored, false);
        source.setRotation(3);
        StoragePile pile = source.pile();
        check(pile != null, "Storage pile did not resolve");
        pile.setPermanent(true);

        loaded.load(source.getUpdateTag());

        ItemStack restored = loaded.getItems().getStackInSlot(7);
        checkEquals(Items.STONE, restored.getItem(), "Restored Storage item");
        checkEquals(23, restored.getCount(), "Restored Storage count");
        checkEquals(identity, restored.getTag(), "Restored Storage tag");
        checkEquals(3, loaded.getRotation(), "Restored Storage rotation");
        check(loaded.isPermanent(), "Restored Storage permanence");
        helper.succeed();
    }

    @GameTest(template = GameTestSupport.TEMPLATE)
    public static void singlesUpdateTagRoundTripsItemsAndBothRotations(
            GameTestHelper helper) {
        SinglesStackBE source = GameTestSupport.placeSingles(helper, ORIGIN);
        SinglesStackBE loaded = GameTestSupport.placeSingles(helper, ORIGIN.east(3));
        source.getItems().insertItem(21, new ItemStack(Items.APPLE), false);
        source.setRotation(2);
        source.setCubeRotation(21, 3);

        loaded.load(source.getUpdateTag());

        checkEquals(Items.APPLE, loaded.getItems().getStackInSlot(21).getItem(),
                "Restored Singles item");
        checkEquals(2, loaded.getRotation(), "Restored Singles block rotation");
        checkEquals(3, loaded.getCubeRotation(21), "Restored Singles item rotation");
        helper.succeed();
    }

    @GameTest(template = GameTestSupport.TEMPLATE)
    public static void barUpdateTagRoundTripsItems(GameTestHelper helper) {
        BarStackBE source = GameTestSupport.placeBar(helper, ORIGIN);
        BarStackBE loaded = GameTestSupport.placeBar(helper, ORIGIN.east(3));
        Item barItem = GameTestSupport.firstBarItem();
        source.getItems().insertItem(37, new ItemStack(barItem), false);

        loaded.load(source.getUpdateTag());

        checkEquals(barItem, loaded.getItems().getStackInSlot(37).getItem(),
                "Restored Bar item");
        checkEquals(1, loaded.getItems().getStackInSlot(37).getCount(),
                "Restored Bar count");
        helper.succeed();
    }

    @GameTest(template = GameTestSupport.TEMPLATE)
    public static void cachedShapesInvalidateWhenContentsChange(GameTestHelper helper) {
        SinglesStackBE singles = GameTestSupport.placeSingles(helper, ORIGIN);
        BarStackBE bar = GameTestSupport.placeBar(helper, ORIGIN.east(3));
        Item barItem = GameTestSupport.firstBarItem();

        check(singles.getCachedShape().isEmpty(), "Empty Singles shape was not empty");
        check(bar.getCachedShape().isEmpty(), "Empty Bar shape was not empty");
        singles.getItems().insertItem(0, new ItemStack(Items.APPLE), false);
        bar.getItems().insertItem(0, new ItemStack(barItem), false);

        check(!singles.getCachedShape().isEmpty(),
                "Singles shape cache did not reflect insertion");
        check(!bar.getCachedShape().isEmpty(),
                "Bar shape cache did not reflect insertion");
        singles.getItems().extractItem(0, 1, false);
        bar.getItems().extractItem(0, 1, false);
        check(singles.getCachedShape().isEmpty(),
                "Singles shape cache did not reflect extraction");
        check(bar.getCachedShape().isEmpty(),
                "Bar shape cache did not reflect extraction");
        helper.succeed();
    }

    @GameTest(template = GameTestSupport.TEMPLATE)
    public static void capabilitySimulationDoesNotMutateAnyStack(GameTestHelper helper) {
        SinglesStackBE singles = GameTestSupport.placeSingles(helper, ORIGIN);
        IItemHandler capability = GameTestSupport.capability(singles);
        ItemStack offered = new ItemStack(Items.APPLE, 5);

        // Cell 0 is a bottom-layer cell of a column standing on the world, so it is grounded
        // outright and the simulation has something to accept.
        ItemStack remainder = capability.insertItem(0, offered, true);

        checkEquals(5, offered.getCount(), "Simulation mutated its input");
        checkEquals(0, GameTestSupport.count(capability, Items.APPLE),
                "Simulation mutated the column");
        checkEquals(4, remainder.getCount(),
                "A cell takes one item, so one call should accept one");
        helper.succeed();
    }
}
