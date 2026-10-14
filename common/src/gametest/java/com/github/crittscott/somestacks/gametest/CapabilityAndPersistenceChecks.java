package com.github.crittscott.somestacks.gametest;

import com.github.crittscott.somestacks.block.BarStackBE;
import com.github.crittscott.somestacks.block.SinglesStackBE;
import com.github.crittscott.somestacks.block.StoragePile;
import com.github.crittscott.somestacks.block.StorageStackBE;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import static com.github.crittscott.somestacks.gametest.GameTestScaffold.ORIGIN;
import static com.github.crittscott.somestacks.gametest.GameTestScaffold.check;
import static com.github.crittscott.somestacks.gametest.GameTestScaffold.checkEquals;
import static com.github.crittscott.somestacks.gametest.GameTestScaffold.firstBarItem;
import static com.github.crittscott.somestacks.gametest.GameTestScaffold.placeBar;
import static com.github.crittscott.somestacks.gametest.GameTestScaffold.placeSingles;
import static com.github.crittscott.somestacks.gametest.GameTestScaffold.placeStorage;

/**
 * The saved-state half of the automation surface: each type's update tag round-tripping the
 * contents and presentation state it is responsible for, and cached-shape invalidation. The
 * automation-surface half (whole-run capability/Transfer-API presence and headroom) stays in each
 * loader's own {@code CapabilityAndPersistenceGameTests}, since it addresses the loader-native
 * storage view directly.
 */
public final class CapabilityAndPersistenceChecks {
    private CapabilityAndPersistenceChecks() {}

    /**
     * Internal update-tag round trip: copy a Storage block's tag into a second block and assert 23
     * tagged stone in slot 7, three quarter-turns of rotation, and permanence. This does not
     * exercise a disk save or reconnect.
     *
     * <p>Nearest manual check: deposit tagged stone, rotate with a redstone torch, toggle
     * permanence, then leave and reload the world. Contents, tags, rotation, and permanence should
     * persist; the exact slot and tag-copy path remain internal.
     */
    public static void storageUpdateTagRoundTripsItemsRotationAndPermanence(GameTestHelper helper) {
        StorageStackBE source = placeStorage(helper, ORIGIN);
        StorageStackBE loaded = placeStorage(helper, ORIGIN.east(3));
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

    /**
     * Internal update-tag round trip: copy a Singles tag into a second block and assert an apple in
     * slot 21, two block quarter-turns, and three item quarter-turns. No disk save is performed.
     *
     * <p>Nearest manual check: build a supported apple cell, rotate the block twice with a redstone
     * torch and the apple three times with a soul torch, then reload the world. Both rotations and
     * the item should persist.
     */
    public static void singlesUpdateTagRoundTripsItemsAndBothRotations(GameTestHelper helper) {
        SinglesStackBE source = placeSingles(helper, ORIGIN);
        SinglesStackBE loaded = placeSingles(helper, ORIGIN.east(3));
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

    /**
     * Internal update-tag round trip: copy a Bar tag with one accepted ingot in slot 37 into a
     * second block and assert its item and count. The fixture bypasses support and does not save to
     * disk.
     *
     * <p>Nearest manual check: build a supported Bar layout, reload the world, and confirm its
     * contents and positions persist.
     */
    public static void barUpdateTagRoundTripsItems(GameTestHelper helper) {
        BarStackBE source = placeBar(helper, ORIGIN);
        BarStackBE loaded = placeBar(helper, ORIGIN.east(3));
        Item barItem = firstBarItem();
        source.getItems().insertItem(37, new ItemStack(barItem), false);

        loaded.load(source.getUpdateTag());

        checkEquals(barItem, loaded.getItems().getStackInSlot(37).getItem(),
                "Restored Bar item");
        checkEquals(1, loaded.getItems().getStackInSlot(37).getCount(),
                "Restored Bar count");
        helper.succeed();
    }

    /**
     * Internal cache check: query empty Singles and Bar shapes, directly insert into bottom slot 0,
     * then extract again. Cached shapes become occupied and empty immediately.
     *
     * <p>Nearest manual check: create each type with one item and check collision around its
     * occupied position; after extraction no stale occupied collision should remain. Empty blocks
     * may be removed, so direct cache identity is not observable.
     */
    public static void cachedShapesInvalidateWhenContentsChange(GameTestHelper helper) {
        SinglesStackBE singles = placeSingles(helper, ORIGIN);
        BarStackBE bar = placeBar(helper, ORIGIN.east(3));
        Item barItem = firstBarItem();

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
}
