package com.github.crittscott.somestacks.gametest;

import com.github.crittscott.somestacks.CommonRegistry;
import com.github.crittscott.somestacks.block.BarStackBE;
import com.github.crittscott.somestacks.block.ShapedStackBlock;
import com.github.crittscott.somestacks.block.SinglesStackBE;
import com.github.crittscott.somestacks.block.SinglesStackBlock;
import com.github.crittscott.somestacks.block.StackBlock;
import com.github.crittscott.somestacks.block.StackBlockEntity;
import com.github.crittscott.somestacks.block.StoragePile;
import com.github.crittscott.somestacks.block.StorageStackBE;
import com.github.crittscott.somestacks.block.StorageStackBlock;
import com.github.crittscott.somestacks.server.ServerGestureState;
import com.github.crittscott.somestacks.util.StackItemStorage;
import com.github.crittscott.somestacks.util.StackMode;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.StringTag;
import net.minecraft.resources.RegistryOps;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.List;
import java.util.function.Function;

import static com.github.crittscott.somestacks.gametest.GameTestScaffold.ORIGIN;
import static com.github.crittscott.somestacks.gametest.GameTestScaffold.check;
import static com.github.crittscott.somestacks.gametest.GameTestScaffold.checkEquals;
import static com.github.crittscott.somestacks.gametest.GameTestScaffold.firstBarItem;
import static com.github.crittscott.somestacks.gametest.GameTestScaffold.placeBar;
import static com.github.crittscott.somestacks.gametest.GameTestScaffold.placeSingles;
import static com.github.crittscott.somestacks.gametest.GameTestScaffold.placeStorage;

/**
 * Disk persistence, client render tags, saved-world migration/recovery, and block shape contracts.
 * The
 * automation-surface half (whole-run capability/Transfer-API presence and headroom) stays in each
 * loader's own {@code CapabilityAndPersistenceGameTests}, since it addresses the loader-native
 * storage view directly.
 */
public final class CapabilityAndPersistenceChecks {
    /** Data version written by Minecraft 1.21.11, the target of the preceding Some Stacks release. */
    private static final int DATA_VERSION_1_21_11 = 4671;

    private CapabilityAndPersistenceChecks() {}

    /**
     * Storage disk data from the current format and the 1.21.11 release preserves items, data components,
     * block-state rotation, and permanence. To
     * reproduce in-game: deposit a component-bearing item, rotate the block, make the pile
     * permanent, then save and restart the world. Verify the item and its components by extraction,
     * the layout by observation, and permanence by emptying the pile and checking that it remains.
     * Also checks a literal 1.21.11 release fixture and failure without partial saves.
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
        pile.setPermanent(true, null);

        CompoundTag saved = source.saveWithoutMetadata(registries);
        copyFacing(helper, source, loaded);
        for (int version : new int[] {
                SharedConstants.getCurrentVersion().dataVersion().version(), DATA_VERSION_1_21_11}) {
            CompoundTag input = saved.copy();
            input.putInt("DataVersion", version);
            loaded.loadWithComponents(TagValueInput.create(ProblemReporter.DISCARDING, registries, input));

            ItemStack restored = loaded.getItems().getStackInSlot(7);
            checkEquals(Items.STONE, restored.getItem(), "Restored Storage item");
            checkEquals(23, restored.getCount(), "Restored Storage count");
            checkEquals(CustomData.of(identity), restored.get(DataComponents.CUSTOM_DATA),
                    "Restored Storage custom data");
            checkEquals(3, loaded.getRotation(), "Restored Storage rotation");
            check(loaded.isPermanent(), "Restored Storage permanence");
            CompoundTag persisted = loaded.saveWithoutMetadata(registries);
            checkEquals(SharedConstants.getCurrentVersion().dataVersion().version(),
                    persisted.getInt("DataVersion").orElseThrow(), "Saved data version");
        }
        checkPreviousReleaseBaseline(helper, loaded);
        checkFailedItemEncoding(helper, loaded);
        helper.succeed();
    }

    /**
     * Reproduces a 1.21.11 release save with custom data, a slotted attribute modifier, and a
     * recoverable retry. In-game: deposit a custom-component stone with the 1.21.11 release, make the
     * pile permanent, save, then open the copied world in this version and extract it.
     */
    private static void checkPreviousReleaseBaseline(GameTestHelper helper, StorageStackBE loaded) {
        var registries = helper.getLevel().registryAccess();
        CompoundTag saved = new CompoundTag();
        saved.putInt("DataVersion", DATA_VERSION_1_21_11);
        saved.putBoolean("Permanent", true);
        CompoundTag item = new CompoundTag();
        item.putString("id", "minecraft:stone");
        item.putInt("count", 5);
        item.putInt("Slot", 4);
        CompoundTag custom = new CompoundTag();
        custom.putString("baseline", "1.21.11");
        CompoundTag modifier = new CompoundTag();
        modifier.putString("id", "somestacks:baseline");
        modifier.putString("type", "minecraft:attack_damage");
        modifier.putDouble("amount", 2.0);
        modifier.putString("operation", "add_value");
        modifier.putString("slot", "mainhand");
        ListTag modifiers = new ListTag();
        modifiers.add(modifier);
        CompoundTag components = new CompoundTag();
        components.put("minecraft:custom_data", custom);
        components.put("minecraft:attribute_modifiers", modifiers);
        item.put("components", components);
        ListTag entries = new ListTag();
        entries.add(item);
        // A duplicate occupied slot must survive instead of overwriting the ordinary entry.
        CompoundTag duplicate = item.copy();
        duplicate.putInt("count", 7);
        entries.add(duplicate);
        ListTag retries = new ListTag();
        CompoundTag retry = item.copy();
        retry.putInt("Slot", 6);
        retries.add(retry);
        CompoundTag inventory = new CompoundTag();
        inventory.put("Items", entries);
        inventory.put("SetAside", retries);
        saved.put("Items", inventory);
        loaded.loadWithComponents(TagValueInput.create(ProblemReporter.DISCARDING, registries, saved));
        ItemStack restored = loaded.getItems().getStackInSlot(4);
        checkEquals(Items.STONE, restored.getItem(), "Release-baseline item");
        checkEquals(5, restored.getCount(), "Release-baseline count");
        checkEquals(CustomData.of(custom), restored.get(DataComponents.CUSTOM_DATA), "Baseline custom data");
        check(restored.has(DataComponents.ATTRIBUTE_MODIFIERS), "Baseline attribute component lost");
        checkEquals(5, loaded.getItems().getStackInSlot(6).getCount(), "Baseline retry did not recover");
        check(loaded.isPermanent(), "Baseline permanence lost");
        CompoundTag persisted = loaded.saveWithoutMetadata(registries);
        CompoundTag encodedItem = persisted.getCompound("Items").orElseThrow()
                .getList("Items").orElseThrow().getCompound(0).orElseThrow();
        ListTag encodedModifiers = encodedItem.getCompound("components").orElseThrow()
                .getList("minecraft:attribute_modifiers").orElseThrow();
        checkEquals(1, encodedModifiers.size(), "Baseline modifier count changed");
        CompoundTag encodedModifier = encodedModifiers.getCompound(0).orElseThrow();
        checkEquals("somestacks:baseline", encodedModifier.getString("id").orElseThrow(), "Baseline modifier identity");
        checkEquals(2.0, encodedModifier.getDouble("amount").orElseThrow(), "Baseline modifier amount");
        ListTag retained = persisted.getCompound("Items").orElseThrow().getList("SetAside").orElseThrow();
        checkEquals(1, retained.size(), "Duplicate occupied baseline slot was not retained");
        checkEquals(7, retained.getCompound(0).orElseThrow().getInt("count").orElseThrow(),
                "Duplicate baseline count changed");
        loaded.loadWithComponents(TagValueInput.create(ProblemReporter.DISCARDING, registries, persisted));
        checkEquals(10, GameTestScaffold.count(loaded.getItems(), Items.STONE), "Baseline reload lost or duplicated contents");
    }

    /** Invalid item counts are test-only: encoding must fail before returning a partial inventory. */
    private static void checkFailedItemEncoding(GameTestHelper helper, StorageStackBE loaded) {
        var registries = helper.getLevel().registryAccess();
        ItemStack original = loaded.getItems().getStackInSlot(4).copy();
        ItemStack invalid = original.copy();
        invalid.setCount(Integer.MAX_VALUE);
        loaded.getItems().setStackInSlot(4, invalid);
        boolean failed = false;
        try {
            loaded.saveWithoutMetadata(registries);
        } catch (IllegalStateException expected) {
            failed = true;
            check(expected.getMessage().contains(loaded.getBlockPos().toString()),
                    "Encoding failure omitted the block location");
        } finally {
            loaded.getItems().setStackInSlot(4, original);
        }
        check(failed, "Failed item encoding returned a partial saved inventory");
        checkEquals(original.getCount(), loaded.getItems().getStackInSlot(4).getCount(),
                "Failed save consumed live contents");
    }

    /**
     * Singles disk data from the current format and the 1.21.11 release preserves contents plus block-state
     * and per-item rotations. To reproduce
     * in-game: deposit an item, rotate its block and the item itself, then unload and revisit the
     * chunk after it has saved and unloaded, or restart the world. Both orientations and the item remain unchanged.
     */
    public static void singlesDiskSaveRoundTripsItemsAndBothRotations(GameTestHelper helper) {
        HolderLookup.Provider registries = helper.getLevel().registryAccess();
        SinglesStackBE source = placeSingles(helper, ORIGIN);
        SinglesStackBE loaded = placeSingles(helper, ORIGIN.east(3));
        source.getItems().insertItem(21, new ItemStack(Items.APPLE), false);
        source.setRotation(2);
        source.setCubeRotation(21, 3);

        CompoundTag saved = source.saveWithoutMetadata(registries);
        copyFacing(helper, source, loaded);
        for (int version : new int[] {
                SharedConstants.getCurrentVersion().dataVersion().version(), DATA_VERSION_1_21_11}) {
            CompoundTag input = saved.copy();
            input.putInt("DataVersion", version);
            loaded.loadWithComponents(TagValueInput.create(ProblemReporter.DISCARDING, registries, input));

            checkEquals(Items.APPLE, loaded.getItems().getStackInSlot(21).getItem(),
                    "Restored Singles item");
            checkEquals(2, loaded.getRotation(), "Restored Singles block rotation");
            checkEquals(3, loaded.getCubeRotation(21), "Restored Singles item rotation");
            CompoundTag persisted = loaded.saveWithoutMetadata(registries);
            checkEquals(SharedConstants.getCurrentVersion().dataVersion().version(),
                    persisted.getInt("DataVersion").orElseThrow(), "Saved data version");
        }
        helper.succeed();
    }

    /**
     * Bar disk data from the current format and the 1.21.11 release preserves the item in each position. To reproduce in-game: deposit Bars,
     * save and restart the world, and verify the same Bar positions
     * remain occupied.
     */
    public static void barDiskSaveRoundTripsItems(GameTestHelper helper) {
        HolderLookup.Provider registries = helper.getLevel().registryAccess();
        BarStackBE source = placeBar(helper, ORIGIN);
        BarStackBE loaded = placeBar(helper, ORIGIN.east(3));
        Item barItem = firstBarItem();
        source.getItems().insertItem(37, new ItemStack(barItem), false);

        CompoundTag saved = source.saveWithoutMetadata(registries);
        for (int version : new int[] {
                SharedConstants.getCurrentVersion().dataVersion().version(), DATA_VERSION_1_21_11}) {
            CompoundTag input = saved.copy();
            input.putInt("DataVersion", version);
            loaded.loadWithComponents(TagValueInput.create(ProblemReporter.DISCARDING, registries, input));

            checkEquals(barItem, loaded.getItems().getStackInSlot(37).getItem(),
                    "Restored Bar item");
            checkEquals(1, loaded.getItems().getStackInSlot(37).getCount(),
                    "Restored Bar count");
            CompoundTag persisted = loaded.saveWithoutMetadata(registries);
            checkEquals(SharedConstants.getCurrentVersion().dataVersion().version(),
                    persisted.getInt("DataVersion").orElseThrow(), "Saved data version");
        }
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
        HolderLookup.Provider registries = helper.getLevel().registryAccess();
        StorageStackBE storage = placeStorage(helper, ORIGIN);
        SinglesStackBE singles = placeSingles(helper, ORIGIN.east(3));
        BarStackBE bars = placeBar(helper, ORIGIN.east(6));
        Item barItem = firstBarItem();
        storage.getItems().insertItem(4, new ItemStack(Items.STONE, 3), false);
        storage.setRotation(2);
        storage.pile().setPermanent(true, null);
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
        check(!storageTag.getCompound("Items").orElseThrow().contains("SetAside"),
                "Storage update tag exposed set-aside data");
        check(!singlesTag.getCompound("Items").orElseThrow().contains("SetAside"),
                "Singles update tag exposed set-aside data");
        check(!barTag.getCompound("Items").orElseThrow().contains("SetAside"),
                "Bar update tag exposed set-aside data");
        check(!storageTag.contains("Permanent"),
                "Storage update tag exposed permanence");
        copyFacing(helper, storage, storageClient);
        copyFacing(helper, singles, singlesClient);
        storageClient.handleUpdateTag(TagValueInput.create(ProblemReporter.DISCARDING, registries, storageTag));
        singlesClient.handleUpdateTag(TagValueInput.create(ProblemReporter.DISCARDING, registries, singlesTag));
        barsClient.handleUpdateTag(TagValueInput.create(ProblemReporter.DISCARDING, registries, barTag));

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
        CompoundTag outsideRange = (CompoundTag) ItemStack.CODEC.encodeStart(RegistryOps.create(NbtOps.INSTANCE, registries),
                new ItemStack(Items.STONE, 5)).getOrThrow();
        outsideRange.putInt("Slot", StorageStackBE.SLOTS + 10);
        ListTag entries = new ListTag();
        entries.add(unknown.copy());
        entries.add(outsideRange.copy());
        CompoundTag missingSlot = outsideRange.copy();
        missingSlot.remove("Slot");
        entries.add(missingSlot.copy());
        StringTag unexpected = StringTag.valueOf("unrecognized saved entry");
        entries.add(unexpected);
        CompoundTag storage = new CompoundTag();
        storage.put("Items", entries);
        CompoundTag saved = new CompoundTag();
        saved.put("Items", storage);
        NbtUtils.addCurrentDataVersion(saved);

        loaded.loadWithComponents(TagValueInput.create(ProblemReporter.DISCARDING, registries, saved));
        checkEquals(0, GameTestScaffold.occupied(loaded.getItems()),
                "Unreadable entries reached live slots");

        var probe = new StackItemStorage(StorageStackBE.SLOTS);
        checkEquals(new StackItemStorage.LoadResult(4, 0), probe.deserializeNBT(registries, storage),
                "New retention diagnostic counts");
        checkEquals(new StackItemStorage.LoadResult(0, 4),
                probe.deserializeNBT(registries, probe.serializeNBT(registries)), "Persisted retry diagnostic counts");

        CompoundTag persisted = loaded.saveWithoutMetadata(registries);
        ListTag setAside = persisted.getCompound("Items").orElseThrow()
                .getList("SetAside").orElseThrow();
        checkEquals(4, setAside.size(), "Persisted set-aside entry count");
        checkEquals(unknown, setAside.getCompound(0).orElseThrow(), "Unknown item tag changed");
        checkEquals(outsideRange, setAside.getCompound(1).orElseThrow(), "Out-of-range item tag changed");
        checkEquals(missingSlot, setAside.get(2), "Missing-slot item tag changed");
        checkEquals(unexpected, setAside.get(3), "Non-compound entry changed");
        check(!loaded.getUpdateTag(registries).getCompound("Items").orElseThrow().contains("SetAside"),
                "Client update tag exposed set-aside entries");
        helper.succeed();
    }

    /**
     * Set-aside entries in 1.21.11 release data recover only into free valid slots and disappear
     * from retained disk data.
     * To reproduce recovery in-game: save a stack holding a mod item, reopen and save the world
     * with that item mod absent, then restore the mod and reopen the world. The item returns.
     * Occupied-slot and invalid-slot retention require inspecting the saved tag.
     */
    public static void setAsideItemsRecoverIntoFreeSlots(GameTestHelper helper) {
        var registries = helper.getLevel().registryAccess();
        StorageStackBE loaded = placeStorage(helper, ORIGIN);
        var storage = new StackItemStorage(StorageStackBE.SLOTS);
        storage.setStackInSlot(0, new ItemStack(Items.STONE, 3));
        CompoundTag inventory = storage.serializeNBT(registries);
        ListTag retries = new ListTag();
        for (int slot : new int[] {1, 0, StorageStackBE.SLOTS}) {
            CompoundTag entry = (CompoundTag) ItemStack.CODEC.encodeStart(RegistryOps.create(NbtOps.INSTANCE, registries),
                    new ItemStack(Items.DIAMOND, 2)).getOrThrow();
            entry.putInt("Slot", slot);
            retries.add(entry);
        }
        inventory.put("SetAside", retries.copy());
        checkEquals(new StackItemStorage.LoadResult(0, 2), storage.deserializeNBT(registries, inventory),
                "Recovery counted a restored entry as a failed retry");
        CompoundTag saved = new CompoundTag();
        saved.putInt("DataVersion", DATA_VERSION_1_21_11);
        saved.put("Items", inventory);
        loaded.loadWithComponents(TagValueInput.create(ProblemReporter.DISCARDING, registries, saved));
        checkEquals(Items.STONE, loaded.getItems().getStackInSlot(0).getItem(), "Occupied slot replaced");
        checkEquals(Items.DIAMOND, loaded.getItems().getStackInSlot(1).getItem(), "Retry did not recover");
        checkEquals(2, loaded.getItems().getStackInSlot(1).getCount(), "Recovered count");
        CompoundTag persisted = loaded.saveWithoutMetadata(registries);
        ListTag remaining = persisted.getCompound("Items").orElseThrow().getList("SetAside").orElseThrow();
        checkEquals(2, remaining.size(), "Recovered retry remained set aside");
        checkEquals(retries.getCompound(1).orElseThrow(), remaining.getCompound(0).orElseThrow(), "Occupied retry changed");
        checkEquals(retries.getCompound(2).orElseThrow(), remaining.getCompound(1).orElseThrow(), "Out-of-range retry changed");
        loaded.loadWithComponents(TagValueInput.create(ProblemReporter.DISCARDING, registries, persisted));
        checkEquals(2, GameTestScaffold.count(loaded.getItems(), Items.DIAMOND), "Reload duplicated recovered item");
        helper.succeed();
    }

    /**
     * A Storage Stack saved by the Some Stacks 1.21.11 release keeps its contents and permanence, and
     * the block is marked for saving in the current data version, whether it loads in place or
     * before chunk attachment. To reproduce in-game: with the 1.21.11 release, deposit stone into a
     * Storage Stack, make the pile permanent, and quit; open the world with this version and
     * extract the stone, then empty the pile. The stone is all there and the empty block remains.
     */
    public static void previousReleaseSaveUpgradesOnLoad(GameTestHelper helper) {
        HolderLookup.Provider registries = helper.getLevel().registryAccess();
        StorageStackBE loaded = placeStorage(helper, ORIGIN);

        CompoundTag item = new CompoundTag();
        item.putString("id", "minecraft:stone");
        item.putInt("count", 5);
        item.putInt("Slot", 4);
        ListTag list = new ListTag();
        list.add(item);
        CompoundTag storage = new CompoundTag();
        storage.put("Items", list);
        CompoundTag saved = new CompoundTag();
        saved.putInt("DataVersion", DATA_VERSION_1_21_11);
        saved.put("Items", storage);
        saved.putBoolean("Permanent", true);

        ChunkAccess chunk = helper.getLevel().getChunk(loaded.getBlockPos());
        chunk.tryMarkSaved();
        loaded.loadCustomOnly(TagValueInput.create(ProblemReporter.DISCARDING, registries, saved));

        ItemStack restored = loaded.getItems().getStackInSlot(4);
        checkEquals(Items.STONE, restored.getItem(), "Upgraded Storage item");
        checkEquals(5, restored.getCount(), "Upgraded Storage count");
        check(loaded.isPermanent(), "Upgraded Storage permanence");
        checkEquals(SharedConstants.getCurrentVersion().dataVersion().version(),
                loaded.saveWithoutMetadata(registries).getInt("DataVersion").orElseThrow(),
                "Upgraded Storage data version");
        check(chunk.isUnsaved(), "Upgrading saved contents did not mark the block for saving");

        // Load without a level, as chunk deserialization may do before attachment.
        StorageStackBE detached = new StorageStackBE(loaded.getBlockPos(), loaded.getBlockState());
        detached.loadWithComponents(TagValueInput.create(ProblemReporter.DISCARDING, registries, saved));
        chunk.tryMarkSaved();
        detached.setLevel(helper.getLevel());
        check(chunk.isUnsaved(), "Deferred migration did not mark the chunk for saving on attachment");
        checkEquals(5, detached.getItems().getStackInSlot(4).getCount(), "Deferred migration lost items");
        helper.succeed();
    }

    /**
     * Structure rotation transforms the outer facing of Storage and Singles while leaving their
     * internal slot identities intact. To reproduce in-game: save either block in a structure and
     * place the structure with a clockwise quarter turn; the visible grid turns with the build.
     */
    public static void structureRotationTransformsRotatableStackFacing(GameTestHelper helper) {
        StorageStackBlock storageBlock =
                (StorageStackBlock) CommonRegistry.storageStackBlock();
        SinglesStackBlock singlesBlock =
                (SinglesStackBlock) CommonRegistry.singlesStackBlock();
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
     * Singles and Bar targeting, outlines, and collision follow occupied contents.
     * To reproduce in-game: use /setblock to place an empty Singles or Bar block, then deposit and
     * extract items in different bottom-layer positions. The outline follows the occupied positions;
     * aim through empty space at an occupied cell in another stack behind it to target that stack.
     * Empty blocks have no outline, collision, or camera fog. Mobs avoid both types.
     * Exact shape and path-type assertions require the GameTest.
     */
    public static void cachedShapesInvalidateWhenContentsChange(
            GameTestHelper helper, Function<ItemStack, ServerPlayer> playerFactory) {
        SinglesStackBE singles = placeSingles(helper, ORIGIN);
        BarStackBE bar = placeBar(helper, ORIGIN.east(3));
        Item barItem = firstBarItem();

        for (StackBlockEntity be : List.of(singles, bar)) {
            assertBlockShapes(helper, be, Shapes.empty());
            ServerPlayer player = playerFactory.apply(ItemStack.EMPTY);
            boolean originalShift = player.isShiftKeyDown();
            player.setShiftKeyDown(false);
            ServerGestureState.set(player, StackMode.STORAGE_STACK, false);
            try {
                var block = (ShapedStackBlock) be.getBlockState().getBlock();
                BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(be.getBlockPos()),
                        Direction.UP, be.getBlockPos(), false);
                checkEquals(InteractionResult.CONSUME, block.useWithoutItem(
                        be.getBlockState(), helper.getLevel(), be.getBlockPos(), player, hit),
                        "Empty block-use was not consumed");
                check(player.getMainHandItem().isEmpty(), "Empty block extracted an item");
            } finally {
                player.setShiftKeyDown(originalShift);
                ServerGestureState.clear(player.getUUID());
            }
        }
        singles.getItems().insertItem(0, new ItemStack(Items.APPLE), false);
        bar.getItems().insertItem(0, new ItemStack(barItem), false);

        check(!singles.getCachedShape().isEmpty(),
                "Singles shape cache did not reflect insertion");
        check(!bar.getCachedShape().isEmpty(),
                "Bar shape cache did not reflect insertion");
        assertBlockShapes(helper, singles, singles.getCachedShape());
        assertBlockShapes(helper, bar, bar.getCachedShape());
        singles.getItems().extractItem(0, 1, false);
        bar.getItems().extractItem(0, 1, false);
        check(singles.getCachedShape().isEmpty(),
                "Singles shape cache did not reflect extraction");
        check(bar.getCachedShape().isEmpty(),
                "Bar shape cache did not reflect extraction");
        assertBlockShapes(helper, singles, Shapes.empty());
        assertBlockShapes(helper, bar, Shapes.empty());
        helper.succeed();
    }

    private static void assertBlockShapes(GameTestHelper helper, StackBlockEntity be, VoxelShape occupied) {
        var block = (ShapedStackBlock) be.getBlockState().getBlock();
        BlockState state = be.getBlockState();
        BlockPos pos = be.getBlockPos();
        var level = helper.getLevel();
        var context = CollisionContext.empty();
        check(!Shapes.joinIsNotEmpty(occupied, block.getShape(state, level, pos, context), BooleanOp.NOT_SAME),
                "Block targeting differs from occupied shape");
        check(!Shapes.joinIsNotEmpty(occupied, block.getCollisionShape(state, level, pos, context), BooleanOp.NOT_SAME),
                "Block collision differs from occupied shape");
        check(!Shapes.joinIsNotEmpty(Shapes.block(), block.getInteractionShape(state, level, pos), BooleanOp.NOT_SAME),
                "Interaction shape is not a full block");
        check(block.getVisualShape(state, level, pos, context).isEmpty(), "Visual shape is not empty");
        for (PathComputationType type : PathComputationType.values()) {
            check(!block.isPathfindable(state, type), "Stack permits pathfinding: " + type);
        }
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
