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
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.entity.BlockEntity;

import static com.github.crittscott.somestacks.gametest.FabricGameTestSupport.ORIGIN;
import static com.github.crittscott.somestacks.gametest.FabricGameTestSupport.check;
import static com.github.crittscott.somestacks.gametest.FabricGameTestSupport.checkEquals;

/**
 * The automation surface and the saved state behind it: the whole-run storage exposed on every
 * side, a run advertising its reachable headroom, and each type's update tag round-tripping the
 * contents and presentation state it is responsible for.
 */
public final class CapabilityAndPersistenceGameTests implements FabricGameTest {
    @GameTest(template = FabricGameTestSupport.TEMPLATE)
    public void itemStorageIsAvailableFromEverySide(GameTestHelper helper) {
        StorageStackBE storage = FabricGameTestSupport.placeStorage(helper, ORIGIN);
        SinglesStackBE singles = FabricGameTestSupport.placeSingles(helper, ORIGIN.east(3));
        BarStackBE bar = FabricGameTestSupport.placeBar(helper, ORIGIN.east(6));

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

    @GameTest(template = FabricGameTestSupport.TEMPLATE)
    public void singleBlockStorageAdvertisesConfiguredHeadroom(GameTestHelper helper) {
        StorageStackBE storage = FabricGameTestSupport.placeStorage(helper, ORIGIN);
        SinglesStackBE singles = FabricGameTestSupport.placeSingles(helper, ORIGIN.east(3));
        BarStackBE bar = FabricGameTestSupport.placeBar(helper, ORIGIN.east(6));

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

    @GameTest(template = FabricGameTestSupport.TEMPLATE)
    public void storageUpdateTagRoundTripsItemsRotationAndPermanence(GameTestHelper helper) {
        StorageStackBE source = FabricGameTestSupport.placeStorage(helper, ORIGIN);
        StorageStackBE loaded = FabricGameTestSupport.placeStorage(helper, ORIGIN.east(3));
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

    @GameTest(template = FabricGameTestSupport.TEMPLATE)
    public void singlesUpdateTagRoundTripsItemsAndBothRotations(GameTestHelper helper) {
        SinglesStackBE source = FabricGameTestSupport.placeSingles(helper, ORIGIN);
        SinglesStackBE loaded = FabricGameTestSupport.placeSingles(helper, ORIGIN.east(3));
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

    @GameTest(template = FabricGameTestSupport.TEMPLATE)
    public void barUpdateTagRoundTripsItems(GameTestHelper helper) {
        BarStackBE source = FabricGameTestSupport.placeBar(helper, ORIGIN);
        BarStackBE loaded = FabricGameTestSupport.placeBar(helper, ORIGIN.east(3));
        Item barItem = FabricGameTestSupport.firstBarItem();
        source.getItems().insertItem(37, new ItemStack(barItem), false);

        loaded.load(source.getUpdateTag());

        checkEquals(barItem, loaded.getItems().getStackInSlot(37).getItem(),
                "Restored Bar item");
        checkEquals(1, loaded.getItems().getStackInSlot(37).getCount(),
                "Restored Bar count");
        helper.succeed();
    }

    @GameTest(template = FabricGameTestSupport.TEMPLATE)
    public void cachedShapesInvalidateWhenContentsChange(GameTestHelper helper) {
        SinglesStackBE singles = FabricGameTestSupport.placeSingles(helper, ORIGIN);
        BarStackBE bar = FabricGameTestSupport.placeBar(helper, ORIGIN.east(3));
        Item barItem = FabricGameTestSupport.firstBarItem();

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

    @GameTest(template = FabricGameTestSupport.TEMPLATE)
    public void simulatedInsertionDoesNotMutateAnyStack(GameTestHelper helper) {
        SinglesStackBE singles = FabricGameTestSupport.placeSingles(helper, ORIGIN);
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
}
