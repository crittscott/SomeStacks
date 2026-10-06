package com.github.crittscott.somestacks.gametest;

import com.github.crittscott.somestacks.block.BarStackBE;
import com.github.crittscott.somestacks.block.SinglesStackBE;
import com.github.crittscott.somestacks.block.SinglesStackBlock;
import com.github.crittscott.somestacks.block.StackBlock;
import com.github.crittscott.somestacks.block.StackBlockEntity;
import com.github.crittscott.somestacks.block.StoragePile;
import com.github.crittscott.somestacks.block.StorageStackBE;
import com.github.crittscott.somestacks.block.StorageStackBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;

import static com.github.crittscott.somestacks.gametest.GameTestScaffold.ORIGIN;
import static com.github.crittscott.somestacks.gametest.GameTestScaffold.check;
import static com.github.crittscott.somestacks.gametest.GameTestScaffold.checkEquals;
import static com.github.crittscott.somestacks.gametest.GameTestScaffold.firstBarItem;
import static com.github.crittscott.somestacks.gametest.GameTestScaffold.placeBar;
import static com.github.crittscott.somestacks.gametest.GameTestScaffold.placeSingles;
import static com.github.crittscott.somestacks.gametest.GameTestScaffold.placeStorage;

/**
 * The saved-state half of the automation surface: each type's update tag round-tripping the
 * contents and presentation state it is responsible for, upgrading a world saved by the previous
 * release, and cached-shape invalidation. The
 * automation-surface half (whole-run capability/Transfer-API presence and headroom) stays in each
 * loader's own {@code CapabilityAndPersistenceGameTests}, since it addresses the loader-native
 * storage view directly.
 */
public final class CapabilityAndPersistenceChecks {
    private CapabilityAndPersistenceChecks() {}

    /**
     * Storage disk data preserves items, data components, block-state rotation, and permanence. To
     * reproduce in-game: deposit a component-bearing item, rotate the block, make the pile
     * permanent, then unload and revisit the chunk or observe it from a joining client. All four
     * properties remain visible.
     */
    public static void storageDiskSaveRoundTripsItemsRotationAndPermanence(GameTestHelper helper) {
        HolderLookup.Provider registries = helper.getLevel().registryAccess();
        StorageStackBE source = placeStorage(helper, ORIGIN);
        StorageStackBE loaded = placeStorage(helper, ORIGIN.east(3));
        CompoundTag identity = new CompoundTag();
        identity.putString("test", "storage");
        ItemStack stored = new ItemStack(Items.STONE, 23);
        stored.set(DataComponents.CUSTOM_DATA, CustomData.of(identity));
        source.getItems().insertItem(7, stored, false);
        source.setRotation(3);
        StoragePile pile = source.pile();
        check(pile != null, "Storage pile did not resolve");
        pile.setPermanent(true);

        CompoundTag saved = source.saveWithoutMetadata(registries);
        check(!saved.contains("Rotation"), "Storage saved legacy block-entity rotation");
        copyFacing(helper, source, loaded);
        loaded.loadWithComponents(saved, registries);

        ItemStack restored = loaded.getItems().getStackInSlot(7);
        checkEquals(Items.STONE, restored.getItem(), "Restored Storage item");
        checkEquals(23, restored.getCount(), "Restored Storage count");
        checkEquals(CustomData.of(identity), restored.get(DataComponents.CUSTOM_DATA),
                "Restored Storage custom data");
        checkEquals(3, loaded.getRotation(), "Restored Storage rotation");
        check(loaded.isPermanent(), "Restored Storage permanence");
        helper.succeed();
    }

    /**
     * Singles disk data preserves contents plus block-state and per-item rotations. To reproduce
     * in-game: deposit an item, rotate its block and the item itself, then unload and revisit the
     * chunk or observe it from a joining client. Both orientations and the item remain unchanged.
     */
    public static void singlesDiskSaveRoundTripsItemsAndBothRotations(GameTestHelper helper) {
        HolderLookup.Provider registries = helper.getLevel().registryAccess();
        SinglesStackBE source = placeSingles(helper, ORIGIN);
        SinglesStackBE loaded = placeSingles(helper, ORIGIN.east(3));
        source.getItems().insertItem(21, new ItemStack(Items.APPLE), false);
        source.setRotation(2);
        source.setCubeRotation(21, 3);

        CompoundTag saved = source.saveWithoutMetadata(registries);
        check(!saved.contains("Rotation"), "Singles saved legacy block-entity rotation");
        copyFacing(helper, source, loaded);
        loaded.loadWithComponents(saved, registries);

        checkEquals(Items.APPLE, loaded.getItems().getStackInSlot(21).getItem(),
                "Restored Singles item");
        checkEquals(2, loaded.getRotation(), "Restored Singles block rotation");
        checkEquals(3, loaded.getCubeRotation(21), "Restored Singles item rotation");
        helper.succeed();
    }

    /**
     * Bar disk data preserves the item in each position. To reproduce in-game: deposit Bars,
     * unload and revisit the chunk or join from another client, and verify the same Bar positions
     * remain occupied.
     */
    public static void barDiskSaveRoundTripsItems(GameTestHelper helper) {
        HolderLookup.Provider registries = helper.getLevel().registryAccess();
        BarStackBE source = placeBar(helper, ORIGIN);
        BarStackBE loaded = placeBar(helper, ORIGIN.east(3));
        Item barItem = firstBarItem();
        source.getItems().insertItem(37, new ItemStack(barItem), false);

        loaded.loadWithComponents(source.saveWithoutMetadata(registries), registries);

        checkEquals(barItem, loaded.getItems().getStackInSlot(37).getItem(),
                "Restored Bar item");
        checkEquals(1, loaded.getItems().getStackInSlot(37).getCount(),
                "Restored Bar count");
        helper.succeed();
    }

    /**
     * Client update tags carry every field the renderers need while omitting server-only state. To
     * reproduce in-game: deposit and rotate Storage, Singles, and Bar contents, make the Storage
     * pile permanent, then join with another client. The contents and rotations appear without
     * exposing permanence or unreadable saved entries to that client.
     */
    public static void updateTagsCarryRenderStateAndOmitServerOnlyData(GameTestHelper helper) {
        HolderLookup.Provider registries = helper.getLevel().registryAccess();
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
        CompoundTag storageTag = storage.getUpdateTag(registries);
        CompoundTag singlesTag = singles.getUpdateTag(registries);
        CompoundTag barTag = bars.getUpdateTag(registries);
        check(!storageTag.contains("Rotation"),
                "Storage update tag carried block-state rotation");
        check(!singlesTag.contains("Rotation"),
                "Singles update tag carried block-state rotation");
        check(!storageTag.getCompound("Items").contains("SetAside"),
                "Storage update tag exposed set-aside data");
        check(!singlesTag.getCompound("Items").contains("SetAside"),
                "Singles update tag exposed set-aside data");
        check(!barTag.getCompound("Items").contains("SetAside"),
                "Bar update tag exposed set-aside data");
        check(!storageTag.contains("Permanent"),
                "Storage update tag exposed permanence");
        copyFacing(helper, storage, storageClient);
        copyFacing(helper, singles, singlesClient);
        storageClient.loadCustomOnly(storageTag, registries);
        singlesClient.loadCustomOnly(singlesTag, registries);
        barsClient.loadCustomOnly(barTag, registries);

        checkEquals(3, storageClient.getItems().getStackInSlot(4).getCount(),
                "Storage client item count");
        checkEquals(2, storageClient.getRotation(), "Storage client rotation");
        check(!storageClient.isPermanent(), "Storage client received permanence");
        checkEquals(Items.APPLE, singlesClient.getItems().getStackInSlot(5).getItem(),
                "Singles client item");
        checkEquals(1, singlesClient.getRotation(), "Singles client block rotation");
        checkEquals(3, singlesClient.getCubeRotation(5), "Singles client item rotation");
        checkEquals(barItem, barsClient.getItems().getStackInSlot(6).getItem(),
                "Bar client item");
        helper.succeed();
    }

    /**
     * Unreadable and unplaceable saved entries remain byte-for-byte in disk data and never enter a
     * live slot. To reproduce in-game: remove an item-providing mod, open and save the world, then
     * restore the mod. Its stored item returns instead of having been discarded.
     */
    public static void unreadableSavedItemsAreKeptAsideOnDisk(GameTestHelper helper) {
        HolderLookup.Provider registries = helper.getLevel().registryAccess();
        StorageStackBE loaded = placeStorage(helper, ORIGIN);

        CompoundTag unknown = new CompoundTag();
        unknown.putString("id", "examplemod:gone");
        unknown.putInt("count", 2);
        unknown.putInt("Slot", 4);
        CompoundTag outsideRange = (CompoundTag) new ItemStack(Items.STONE, 5)
                .save(registries, new CompoundTag());
        outsideRange.putInt("Slot", StorageStackBE.SLOTS + 10);
        ListTag entries = new ListTag();
        entries.add(unknown.copy());
        entries.add(outsideRange.copy());
        CompoundTag storage = new CompoundTag();
        storage.put("Items", entries);
        CompoundTag saved = new CompoundTag();
        saved.put("Items", storage);
        NbtUtils.addCurrentDataVersion(saved);

        loaded.loadWithComponents(saved, registries);
        checkEquals(0, GameTestScaffold.occupied(loaded.getItems()),
                "Unreadable entries reached live slots");

        CompoundTag persisted = loaded.saveWithoutMetadata(registries);
        ListTag setAside = persisted.getCompound("Items")
                .getList("SetAside", Tag.TAG_COMPOUND);
        checkEquals(2, setAside.size(), "Persisted set-aside entry count");
        checkEquals(unknown, setAside.getCompound(0), "Unknown item tag changed");
        checkEquals(outsideRange, setAside.getCompound(1), "Out-of-range item tag changed");
        check(!loaded.getUpdateTag(registries).getCompound("Items").contains("SetAside"),
                "Client update tag exposed set-aside entries");
        helper.succeed();
    }

    /**
     * A Storage Stack saved by Some Stacks for Minecraft 1.21.1 keeps its contents in the current
     * item format, and the block is marked for saving in that format. To reproduce in-game: in a
     * 1.21.1 world, {@code /give @s stone[fire_resistant={}] 5}, deposit it into a Storage Stack,
     * and quit; open the world with this version and extract the stone. It is still there, and is
     * still fire resistant through the {@code damage_resistant} component that replaced
     * {@code fire_resistant} in 1.21.2.
     */
    public static void unversionedSaveUpgradesItemsOnLoad(GameTestHelper helper) {
        HolderLookup.Provider registries = helper.getLevel().registryAccess();
        StorageStackBE loaded = placeStorage(helper, ORIGIN);

        CompoundTag components = new CompoundTag();
        components.put("minecraft:fire_resistant", new CompoundTag());
        CompoundTag item = new CompoundTag();
        item.putString("id", "minecraft:stone");
        item.putInt("count", 5);
        item.put("components", components);
        item.putInt("Slot", 4);
        ListTag list = new ListTag();
        list.add(item);
        CompoundTag storage = new CompoundTag();
        storage.put("Items", list);
        CompoundTag saved = new CompoundTag();
        saved.put("Items", storage);
        saved.putInt("Rotation", 3);
        saved.putBoolean("Permanent", false);

        ChunkAccess chunk = helper.getLevel().getChunk(loaded.getBlockPos());
        chunk.tryMarkSaved();
        loaded.loadCustomOnly(saved, registries);

        ItemStack restored = loaded.getItems().getStackInSlot(4);
        checkEquals(Items.STONE, restored.getItem(), "Upgraded Storage item");
        checkEquals(5, restored.getCount(), "Upgraded Storage count");
        check(restored.has(DataComponents.DAMAGE_RESISTANT),
                "The 1.21.1 fire_resistant component was not upgraded to damage_resistant");
        checkEquals(3, loaded.getRotation(), "Migrated Storage block-state rotation");
        check(!loaded.saveWithoutMetadata(registries).contains("Rotation"),
                "Migrated Storage rewrote legacy Rotation");
        check(chunk.isUnsaved(), "Upgrading saved contents did not mark the block for saving");
        helper.succeed();
    }

    /**
     * Structure rotation transforms the outer facing of Storage and Singles while leaving their
     * internal slot identities intact. To reproduce in-game: save either block in a structure and
     * place the structure with a clockwise quarter turn; the visible grid turns with the build.
     */
    public static void structureRotationTransformsRotatableStackFacing(GameTestHelper helper) {
        StorageStackBlock storageBlock =
                (StorageStackBlock) com.github.crittscott.somestacks.CommonRegistry
                        .STORAGE_STACK_BLOCK.get();
        SinglesStackBlock singlesBlock =
                (SinglesStackBlock) com.github.crittscott.somestacks.CommonRegistry
                        .SINGLES_STACK_BLOCK.get();
        BlockState storageState = storageBlock.rotate(
                storageBlock.defaultBlockState(), Rotation.CLOCKWISE_90);
        BlockState singlesState = singlesBlock.rotate(
                singlesBlock.defaultBlockState(), Rotation.CLOCKWISE_90);

        checkEquals(Direction.EAST, storageState.getValue(StackBlock.HORIZONTAL_FACING),
                "Rotated Storage facing");
        checkEquals(Direction.EAST, singlesState.getValue(StackBlock.HORIZONTAL_FACING),
                "Rotated Singles facing");

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
     * Singles and Bar collision and outline shapes update when their contents change. To reproduce
     * in-game: deposit into an empty Singles or Bar block and then extract the item. Its occupied
     * shape appears on deposit and disappears on extraction.
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

    private static void copyFacing(
            GameTestHelper helper, StackBlockEntity source, StackBlockEntity target) {
        BlockState targetState = target.getBlockState().setValue(
                StackBlock.HORIZONTAL_FACING,
                source.getBlockState().getValue(StackBlock.HORIZONTAL_FACING));
        check(helper.getLevel().setBlock(target.getBlockPos(), targetState, Block.UPDATE_ALL),
                "Could not copy stack facing");
    }
}
