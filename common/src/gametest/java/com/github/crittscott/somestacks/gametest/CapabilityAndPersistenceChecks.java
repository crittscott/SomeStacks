package com.github.crittscott.somestacks.gametest;

import com.github.crittscott.somestacks.CommonRegistry;
import com.github.crittscott.somestacks.block.BarStackBE;
import com.github.crittscott.somestacks.block.SinglesStackBE;
import com.github.crittscott.somestacks.block.StackBlockEntity;
import com.github.crittscott.somestacks.block.StoragePile;
import com.github.crittscott.somestacks.block.StorageStackBE;
import com.github.crittscott.somestacks.util.StackItemStorage;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

import static com.github.crittscott.somestacks.gametest.GameTestScaffold.ORIGIN;
import static com.github.crittscott.somestacks.gametest.GameTestScaffold.check;
import static com.github.crittscott.somestacks.gametest.GameTestScaffold.checkEquals;
import static com.github.crittscott.somestacks.gametest.GameTestScaffold.firstBarItem;
import static com.github.crittscott.somestacks.gametest.GameTestScaffold.placeBar;
import static com.github.crittscott.somestacks.gametest.GameTestScaffold.placeSingles;
import static com.github.crittscott.somestacks.gametest.GameTestScaffold.placeStorage;

/**
 * Disk persistence, client update tags and their validation, set-aside recovery, structure
 * rotation, and cached-shape invalidation. The automation-surface half (whole-run
 * capability/Transfer-API presence and headroom) stays in each loader's own
 * {@code CapabilityAndPersistenceGameTests}, since it addresses the loader-native storage view
 * directly.
 */
public final class CapabilityAndPersistenceChecks {
    private CapabilityAndPersistenceChecks() {}

    /**
     * Storage disk data preserves items, tags, block-state rotation, and permanence. To reproduce
     * in-game: deposit tagged stone, rotate the block with a redstone torch, make the pile
     * permanent, then save and restart the world. Verify the stone and its tag by extraction, the
     * layout by observation, and permanence by emptying the pile and checking that it remains.
     */
    public static void storageDiskSaveRoundTripsItemsRotationAndPermanence(GameTestHelper helper) {
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

        CompoundTag saved = source.saveWithoutMetadata();
        check(!saved.contains("Rotation"), "Storage saved block-entity rotation");
        copyFacing(helper, source, loaded);
        loaded.load(saved);

        ItemStack restored = loaded.getItems().getStackInSlot(7);
        checkEquals(Items.STONE, restored.getItem(), "Restored Storage item");
        checkEquals(23, restored.getCount(), "Restored Storage count");
        checkEquals(identity, restored.getTag(), "Restored Storage tag");
        checkEquals(3, loaded.getRotation(), "Restored Storage rotation");
        check(loaded.isPermanent(), "Restored Storage permanence");
        helper.succeed();
    }

    /**
     * Singles disk data preserves contents plus block-state and per-item rotations. To reproduce
     * in-game: build a supported apple cell, rotate the block twice with a redstone torch and the
     * apple three times with a soul torch, then save and restart the world. Both orientations and
     * the item remain unchanged.
     */
    public static void singlesDiskSaveRoundTripsItemsAndBothRotations(GameTestHelper helper) {
        SinglesStackBE source = placeSingles(helper, ORIGIN);
        SinglesStackBE loaded = placeSingles(helper, ORIGIN.east(3));
        source.getItems().insertItem(21, new ItemStack(Items.APPLE), false);
        source.setRotation(2);
        source.setCubeRotation(21, 3);

        CompoundTag saved = source.saveWithoutMetadata();
        check(!saved.contains("Rotation"), "Singles saved block-entity rotation");
        copyFacing(helper, source, loaded);
        loaded.load(saved);

        checkEquals(Items.APPLE, loaded.getItems().getStackInSlot(21).getItem(),
                "Restored Singles item");
        checkEquals(2, loaded.getRotation(), "Restored Singles block rotation");
        checkEquals(3, loaded.getCubeRotation(21), "Restored Singles item rotation");
        helper.succeed();
    }

    /**
     * Bar disk data preserves the item in each position. To reproduce in-game: deposit Bars, save
     * and restart the world, and verify the same Bar positions remain occupied.
     */
    public static void barDiskSaveRoundTripsItems(GameTestHelper helper) {
        BarStackBE source = placeBar(helper, ORIGIN);
        BarStackBE loaded = placeBar(helper, ORIGIN.east(3));
        Item barItem = firstBarItem();
        source.getItems().insertItem(37, new ItemStack(barItem), false);

        loaded.load(source.saveWithoutMetadata());

        checkEquals(barItem, loaded.getItems().getStackInSlot(37).getItem(),
                "Restored Bar item");
        checkEquals(1, loaded.getItems().getStackInSlot(37).getCount(),
                "Restored Bar count");
        helper.succeed();
    }

    /**
     * Client update tags carry every field the renderers need while omitting server-only state. To
     * reproduce the visible portion in-game: deposit into all three types, rotate Storage and
     * Singles, and join with another client. Contents and rotations appear. Verifying omission of
     * Permanent and Items.SetAside requires inspecting the update tags; no purely in-game
     * reproduction proves those fields are absent.
     */
    public static void updateTagsCarryRenderStateAndOmitServerOnlyData(GameTestHelper helper) {
        StorageStackBE storage = placeStorage(helper, ORIGIN);
        SinglesStackBE singles = placeSingles(helper, ORIGIN.east(3));
        BarStackBE bars = placeBar(helper, ORIGIN.east(6));
        Item barItem = firstBarItem();
        storage.getItems().insertItem(4, new ItemStack(Items.STONE, 3), false);
        storage.setRotation(2);
        storage.pile().setPermanent(true);
        singles.getItems().insertItem(5, new ItemStack(Items.APPLE), false);
        singles.setRotation(1);
        singles.setCubeRotation(5, 3);
        bars.getItems().insertItem(6, new ItemStack(barItem), false);

        StorageStackBE storageClient = placeStorage(helper, ORIGIN.south(3));
        SinglesStackBE singlesClient = placeSingles(helper, ORIGIN.east(3).south(3));
        BarStackBE barsClient = placeBar(helper, ORIGIN.east(6).south(3));
        CompoundTag storageTag = storage.getUpdateTag();
        CompoundTag singlesTag = singles.getUpdateTag();
        CompoundTag barTag = bars.getUpdateTag();
        check(!storageTag.contains("Rotation"), "Storage update tag carried block-state rotation");
        check(!singlesTag.contains("Rotation"), "Singles update tag carried block-state rotation");
        check(!storageTag.getCompound("Items").contains("SetAside"),
                "Storage update tag exposed set-aside data");
        check(!singlesTag.getCompound("Items").contains("SetAside"),
                "Singles update tag exposed set-aside data");
        check(!barTag.getCompound("Items").contains("SetAside"),
                "Bar update tag exposed set-aside data");
        check(!storageTag.contains("Permanent"), "Storage update tag exposed permanence");
        copyFacing(helper, storage, storageClient);
        copyFacing(helper, singles, singlesClient);
        check(storageClient.loadClientUpdate(storageTag), "Storage update rejected");
        check(singlesClient.loadClientUpdate(singlesTag), "Singles update rejected");
        check(barsClient.loadClientUpdate(barTag), "Bar update rejected");

        checkEquals(3, storageClient.getItems().getStackInSlot(4).getCount(),
                "Storage client item count");
        checkEquals(2, storageClient.getRotation(), "Storage client rotation");
        check(!storageClient.isPermanent(), "Storage client received permanence");
        checkEquals(Items.APPLE, singlesClient.getItems().getStackInSlot(5).getItem(),
                "Singles client item");
        checkEquals(1, singlesClient.getRotation(), "Singles client block rotation");
        checkEquals(3, singlesClient.getCubeRotation(5), "Singles client item rotation");
        checkEquals(barItem, barsClient.getItems().getStackInSlot(6).getItem(), "Bar client item");
        helper.succeed();
    }

    /**
     * Unreadable and unplaceable saved entries remain unchanged in disk data and never enter a live
     * slot. To reproduce in-game: deposit an item from another mod, quit, remove that mod, open and
     * save the world, then restore the mod and open the world again. The item returns instead of
     * having been discarded, and while the mod is absent the server log names the stack's position.
     */
    public static void unreadableSavedItemsAreKeptAsideOnDisk(GameTestHelper helper) {
        StorageStackBE loaded = placeStorage(helper, ORIGIN);

        CompoundTag unknown = new CompoundTag();
        unknown.putString("id", "examplemod:gone");
        unknown.putByte("Count", (byte) 2);
        unknown.putInt("Slot", 4);
        CompoundTag malformed = new CompoundTag();
        malformed.putString("id", "Not An Id!");
        malformed.putByte("Count", (byte) 1);
        malformed.putInt("Slot", 5);
        CompoundTag outsideRange = new ItemStack(Items.STONE, 5).save(new CompoundTag());
        outsideRange.putInt("Slot", StorageStackBE.SLOTS + 10);
        ListTag entries = new ListTag();
        entries.add(unknown.copy());
        entries.add(malformed.copy());
        entries.add(outsideRange.copy());
        CompoundTag storage = new CompoundTag();
        storage.put("Items", entries);
        CompoundTag saved = new CompoundTag();
        saved.put("Items", storage);
        NbtUtils.addCurrentDataVersion(saved);

        loaded.load(saved);
        checkEquals(0, GameTestScaffold.occupied(loaded.getItems()),
                "Unreadable entries reached live slots");

        StackItemStorage probe = new StackItemStorage(StorageStackBE.SLOTS);
        checkEquals(new StackItemStorage.LoadResult(3, 0), probe.deserializeNBT(storage),
                "New retention diagnostic counts");
        checkEquals(new StackItemStorage.LoadResult(0, 3), probe.deserializeNBT(probe.serializeNBT()),
                "Persisted retry diagnostic counts");

        CompoundTag persisted = loaded.saveWithoutMetadata();
        ListTag setAside = persisted.getCompound("Items").getList("SetAside", Tag.TAG_COMPOUND);
        checkEquals(3, setAside.size(), "Persisted set-aside entry count");
        checkEquals(unknown, setAside.getCompound(0), "Unknown item tag changed");
        checkEquals(malformed, setAside.getCompound(1), "Malformed item tag changed");
        checkEquals(outsideRange, setAside.getCompound(2), "Out-of-range item tag changed");
        check(!loaded.getUpdateTag().getCompound("Items").contains("SetAside"),
                "Client update tag exposed set-aside entries");
        helper.succeed();
    }

    /**
     * Set-aside entries recover only into free valid slots and then leave the retained disk data.
     * To reproduce recovery in-game: deposit an item from another mod, quit, open and save the
     * world with that mod absent, then restore the mod and open the world. The item returns.
     * Occupied-slot and invalid-slot retention require inspecting the saved tag.
     */
    public static void setAsideItemsRecoverIntoFreeSlots(GameTestHelper helper) {
        StorageStackBE loaded = placeStorage(helper, ORIGIN);
        StackItemStorage storage = new StackItemStorage(StorageStackBE.SLOTS);
        storage.setStackInSlot(0, new ItemStack(Items.STONE, 3));
        CompoundTag inventory = storage.serializeNBT();
        ListTag retries = new ListTag();
        for (int slot : new int[] {1, 0, StorageStackBE.SLOTS}) {
            CompoundTag entry = new ItemStack(Items.DIAMOND, 2).save(new CompoundTag());
            entry.putInt("Slot", slot);
            retries.add(entry);
        }
        inventory.put("SetAside", retries.copy());
        checkEquals(new StackItemStorage.LoadResult(0, 2), storage.deserializeNBT(inventory),
                "Recovery counted a restored entry as a failed retry");
        CompoundTag saved = new CompoundTag();
        NbtUtils.addCurrentDataVersion(saved);
        saved.put("Items", inventory);
        loaded.load(saved);
        checkEquals(Items.STONE, loaded.getItems().getStackInSlot(0).getItem(), "Occupied slot replaced");
        checkEquals(Items.DIAMOND, loaded.getItems().getStackInSlot(1).getItem(), "Retry did not recover");
        checkEquals(2, loaded.getItems().getStackInSlot(1).getCount(), "Recovered count");
        CompoundTag persisted = loaded.saveWithoutMetadata();
        ListTag remaining = persisted.getCompound("Items").getList("SetAside", Tag.TAG_COMPOUND);
        checkEquals(2, remaining.size(), "Recovered retry remained set aside");
        checkEquals(retries.getCompound(1), remaining.getCompound(0), "Occupied retry changed");
        checkEquals(retries.getCompound(2), remaining.getCompound(1), "Out-of-range retry changed");
        loaded.load(persisted);
        checkEquals(2, GameTestScaffold.count(loaded.getItems(), Items.DIAMOND),
                "Reload duplicated recovered item");
        helper.succeed();
    }

    /**
     * A client update replaces local contents only when the whole tag is valid. No in-game
     * reproduction applies: a well-behaved server never sends a malformed update.
     */
    public static void clientUpdatesRejectMalformedDataAtomically(GameTestHelper helper) {
        StackBlockEntity[] blocks = {
                placeStorage(helper, ORIGIN),
                placeSingles(helper, ORIGIN.east(3)),
                placeBar(helper, ORIGIN.east(6))};
        for (StackBlockEntity be : blocks) {
            boolean bar = be instanceof BarStackBE;
            ItemStack seed = bar ? new ItemStack(firstBarItem()) : new ItemStack(Items.STONE);
            be.getItems().setStackInSlot(0, seed);
            CompoundTag valid = be.getUpdateTag();
            CompoundTag item = valid.getCompound("Items").getList("Items", Tag.TAG_COMPOUND).getCompound(0);

            CompoundTag oversized = valid.copy();
            ListTag tooMany = new ListTag();
            for (int i = 0; i <= be.getItems().getSlots(); i++) tooMany.add(item.copy());
            oversized.getCompound("Items").put("Items", tooMany);
            rejectedUpdate(be, oversized, seed);

            CompoundTag duplicate = valid.copy();
            duplicate.getCompound("Items").getList("Items", Tag.TAG_COMPOUND).add(item.copy());
            rejectedUpdate(be, duplicate, seed);
            for (int slot : new int[] {-1, be.getItems().getSlots()}) {
                CompoundTag outOfRange = valid.copy();
                outOfRange.getCompound("Items").getList("Items", Tag.TAG_COMPOUND)
                        .getCompound(0).putInt("Slot", slot);
                rejectedUpdate(be, outOfRange, seed);
            }
            CompoundTag missingSlot = valid.copy();
            missingSlot.getCompound("Items").getList("Items", Tag.TAG_COMPOUND).getCompound(0).remove("Slot");
            rejectedUpdate(be, missingSlot, seed);
            CompoundTag invalidItem = valid.copy();
            invalidItem.getCompound("Items").getList("Items", Tag.TAG_COMPOUND)
                    .getCompound(0).putString("id", "somestacks:nonexistent_item");
            rejectedUpdate(be, invalidItem, seed);
            CompoundTag setAside = valid.copy();
            setAside.getCompound("Items").put(StackItemStorage.TAG_SET_ASIDE, new ListTag());
            rejectedUpdate(be, setAside, seed);
            CompoundTag wrongType = valid.copy();
            wrongType.getCompound("Items").putString("Items", "not a list");
            rejectedUpdate(be, wrongType, seed);
            CompoundTag noVersion = valid.copy();
            noVersion.remove("DataVersion");
            rejectedUpdate(be, noVersion, seed);
            CompoundTag permanent = valid.copy();
            permanent.putBoolean("Permanent", true);
            rejectedUpdate(be, permanent, seed);
            CompoundTag rotation = valid.copy();
            rotation.putInt("Rotation", 1);
            rejectedUpdate(be, rotation, seed);

            CompoundTag replacement = valid.copy();
            StackItemStorage source = new StackItemStorage(be.getItems().getSlots());
            ItemStack replacementItem = bar ? new ItemStack(firstBarItem()) : new ItemStack(Items.DIAMOND);
            source.setStackInSlot(1, replacementItem);
            replacement.put("Items", source.serializeNBT());
            check(be.loadClientUpdate(replacement), "Valid update rejected");
            check(be.getItems().getStackInSlot(0).isEmpty(), "Old client slot survived replacement");
            checkEquals(replacementItem.getItem(), be.getItems().getStackInSlot(1).getItem(),
                    "Replacement client item");
        }
        helper.succeed();
    }

    /**
     * Malformed Singles item-rotation metadata invalidates the entire update. No in-game
     * reproduction applies: a well-behaved server never sends one.
     */
    public static void clientSinglesUpdatesValidateRotationBeforeInventory(GameTestHelper helper) {
        SinglesStackBE be = placeSingles(helper, ORIGIN);
        ItemStack seed = new ItemStack(Items.STONE);
        be.getItems().setStackInSlot(0, seed);
        CompoundTag valid = be.getUpdateTag();
        StackItemStorage replacement = new StackItemStorage(SinglesStackBE.SLOTS);
        replacement.setStackInSlot(1, new ItemStack(Items.DIAMOND));
        CompoundTag changedItems = replacement.serializeNBT();
        for (int[] rotations : new int[][] {new int[1], new int[SinglesStackBE.SLOTS + 1]}) {
            CompoundTag invalid = valid.copy();
            invalid.put("Items", changedItems.copy());
            invalid.putIntArray("CubeRotations", rotations);
            rejectedUpdate(be, invalid, seed);
        }
        for (int rotation : new int[] {-1, 4}) {
            CompoundTag invalid = valid.copy();
            int[] rotations = new int[SinglesStackBE.SLOTS];
            rotations[0] = rotation;
            invalid.put("Items", changedItems.copy());
            invalid.putIntArray("CubeRotations", rotations);
            rejectedUpdate(be, invalid, seed);
        }
        check(be.loadClientUpdate(valid), "Valid rotations rejected");
        helper.succeed();
    }

    private static void rejectedUpdate(StackBlockEntity be, CompoundTag tag, ItemStack seed) {
        check(!be.loadClientUpdate(tag), "Malformed update accepted");
        checkEquals(seed.getItem(), be.getItems().getStackInSlot(0).getItem(),
                "Rejected update changed slot 0");
        check(be.getItems().getStackInSlot(1).isEmpty(), "Rejected update partially changed inventory");
        check(!be.getItems().serializeNBT().contains("SetAside"),
                "Client retained rejected entries for recovery");
    }

    /**
     * Structure rotation and mirroring transform the facing of Storage and Singles, and the layout
     * follows the facing. To reproduce in-game: save a Storage or Singles Stack holding several
     * items in a structure block, then load the structure with a clockwise quarter turn or a mirror;
     * the visible grid turns with the build.
     */
    public static void structureRotationTransformsRotatableStackFacing(GameTestHelper helper) {
        Block storageBlock = CommonRegistry.STORAGE_STACK_BLOCK.get();
        Block singlesBlock = CommonRegistry.SINGLES_STACK_BLOCK.get();
        BlockState storageState = storageBlock.defaultBlockState().rotate(Rotation.CLOCKWISE_90);
        BlockState singlesState = singlesBlock.defaultBlockState().rotate(Rotation.CLOCKWISE_90);

        checkEquals(Direction.EAST, storageState.getValue(BlockStateProperties.HORIZONTAL_FACING),
                "Rotated Storage facing");
        checkEquals(Direction.EAST, singlesState.getValue(BlockStateProperties.HORIZONTAL_FACING),
                "Rotated Singles facing");
        checkEquals(Direction.SOUTH, storageBlock.defaultBlockState().mirror(Mirror.LEFT_RIGHT)
                .getValue(BlockStateProperties.HORIZONTAL_FACING), "Mirrored Storage facing");

        BlockPos storagePos = helper.absolutePos(ORIGIN);
        BlockPos singlesPos = helper.absolutePos(ORIGIN.east(3));
        check(helper.getLevel().setBlock(storagePos, storageState, Block.UPDATE_ALL),
                "Could not place rotated Storage");
        check(helper.getLevel().setBlock(singlesPos, singlesState, Block.UPDATE_ALL),
                "Could not place rotated Singles");
        StorageStackBE storage = (StorageStackBE) helper.getLevel().getBlockEntity(storagePos);
        SinglesStackBE singles = (SinglesStackBE) helper.getLevel().getBlockEntity(singlesPos);
        check(storage != null && storage.getRotation() == 3,
                "Storage geometry did not read the rotated facing");
        check(singles != null && singles.getRotation() == 3,
                "Singles geometry did not read the rotated facing");
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

    private static void copyFacing(GameTestHelper helper, StackBlockEntity source, StackBlockEntity target) {
        BlockState targetState = target.getBlockState().setValue(
                BlockStateProperties.HORIZONTAL_FACING,
                source.getBlockState().getValue(BlockStateProperties.HORIZONTAL_FACING));
        check(helper.getLevel().setBlock(target.getBlockPos(), targetState, Block.UPDATE_ALL),
                "Could not copy stack facing");
    }
}
