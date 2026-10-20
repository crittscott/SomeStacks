package com.github.crittscott.somestacks.gametest;

import com.github.crittscott.somestacks.ServerConfig;
import com.github.crittscott.somestacks.client.StackState;
import com.github.crittscott.somestacks.block.StackBlockEntity;
import com.github.crittscott.somestacks.client.ConfigSyncReceiver;
import com.github.crittscott.somestacks.client.ClientRenderToolState;
import com.github.crittscott.somestacks.network.ConfigSyncPkt;
import com.github.crittscott.somestacks.util.BlockType;
import com.github.crittscott.somestacks.util.StackItemStorage;
import io.netty.buffer.Unpooled;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.List;
import java.util.Collections;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Set;

import static com.github.crittscott.somestacks.gametest.GameTestScaffold.ORIGIN;
import static com.github.crittscott.somestacks.gametest.GameTestScaffold.check;
import static com.github.crittscott.somestacks.gametest.GameTestScaffold.checkEquals;

/** Wire boundaries, independent of forgiving saved-world recovery. */
public final class SynchronizationChecks {
    private SynchronizationChecks() {}

    /**
     * Complete snapshots publish flags and both lists in one operation. To reproduce in-game:
     * configure disabled types and namespace lists, join, and inspect mode cycling and /ss write
     * list. Repeat after a policy edit without reconnecting.
     */
    public static void configSnapshotsRoundTripAndPublishAtomically(GameTestHelper helper) {
        var originalGallery = ClientRenderToolState.genMods();
        var originalDenied = List.copyOf(ClientRenderToolState.disabledMods());
        boolean[] originalFlags = flags();
        try {
            for (int size : new int[] {0, 1, ConfigSyncPkt.MAX_NAMESPACES}) {
                List<String> gallery = new ArrayList<>();
                List<String> denied = new ArrayList<>();
                for (int i = 0; i < size; i++) {
                    String prefix = "namespace" + i;
                    String namespace = prefix + "a".repeat(ConfigSyncPkt.MAX_NAMESPACE_LENGTH - prefix.length());
                    gallery.add(namespace);
                    denied.add(namespace);
                }
                var packet = new ConfigSyncPkt(size == 0, size == 1, size > 1, gallery, denied);
                var raw = Unpooled.buffer();
                try {
                    var buf = new RegistryFriendlyByteBuf(raw, helper.getLevel().registryAccess());
                    ConfigSyncPkt.STREAM_CODEC.encode(buf, packet);
                    check(raw.readableBytes() < 132 * 1024, "Maximum snapshot exceeds its bounded size");
                    var decoded = ConfigSyncPkt.STREAM_CODEC.decode(buf);
                    checkEquals(packet, decoded, "Snapshot codec round trip");
                    checkEquals(0, raw.readableBytes(), "Snapshot decoder left fields unread");
                    ConfigSyncReceiver.apply(decoded);
                } finally {
                    raw.release();
                }
                checkEquals(gallery, ClientRenderToolState.genMods(), "Published gallery list");
                checkEquals(Set.copyOf(denied), ClientRenderToolState.disabledMods(), "Published deny set");
                check(Arrays.equals(new boolean[] {size == 0, size == 1, size > 1}, flags()), "Published flags");
            }
            var defaults = ServerConfig.defaults();
            var normalized = ConfigSyncPkt.fromSettings(new ServerConfig.Settings(defaults.maxPileHeight(),
                    true, true, true, List.of(" Minecraft ", "minecraft"), defaults.disableItems(),
                    defaults.renderGalleryPlacementsPerTick(), false, defaults.galleryPermissionLevel(),
                    List.of(" SomeStacks ", "somestacks"), List.of()));
            checkEquals(List.of("minecraft"), normalized.disabledMods(), "Deny normalization");
            checkEquals(List.of("somestacks"), normalized.genMods(), "Gallery normalization");
        } finally {
            ClientRenderToolState.replace(originalGallery, originalDenied);
            restoreFlags(originalFlags);
        }
        helper.succeed();
    }

    /** No in-game reproduction applies: hostile list counts, names, and duplicate entries must fail decoding. */
    public static void configDecoderRejectsMalformedCountsAndNamespaces(GameTestHelper helper) {
        for (boolean gallery : new boolean[] {true, false}) {
            for (int count : new int[] {-1, ConfigSyncPkt.MAX_NAMESPACES + 1}) {
                var raw = Unpooled.buffer();
                try {
                    var buf = new RegistryFriendlyByteBuf(raw, helper.getLevel().registryAccess());
                    writeFlags(buf);
                    if (!gallery) buf.writeVarInt(0);
                    buf.writeVarInt(count);
                    rejects(() -> ConfigSyncPkt.STREAM_CODEC.decode(buf), "Invalid wire count decoded");
                } finally { raw.release(); }
            }
        }
        for (List<String> namespaces : List.of(List.of(""), List.of("Uppercase"),
                List.of("bad namespace"), List.of("a".repeat(ConfigSyncPkt.MAX_NAMESPACE_LENGTH + 1)),
                List.of("duplicate", "duplicate"))) {
            var raw = Unpooled.buffer();
            try {
                var buf = new RegistryFriendlyByteBuf(raw, helper.getLevel().registryAccess());
                writeFlags(buf);
                buf.writeVarInt(namespaces.size());
                namespaces.forEach(buf::writeUtf);
                buf.writeVarInt(0);
                rejects(() -> ConfigSyncPkt.STREAM_CODEC.decode(buf), "Invalid namespace decoded");
            } finally { raw.release(); }
        }
        rejects(() -> new ConfigSyncPkt(true, true, true,
                Collections.nCopies(ConfigSyncPkt.MAX_NAMESPACES + 1, "namespace"), List.of()),
                "Direct construction bypassed count limit");
        helper.succeed();
    }

    private static void writeFlags(RegistryFriendlyByteBuf buf) {
        buf.writeBoolean(true);
        buf.writeBoolean(true);
        buf.writeBoolean(true);
    }

    private static boolean[] flags() {
        return new boolean[] {StackState.isBlockTypeEnabled(BlockType.STORAGE_STACK),
                StackState.isBlockTypeEnabled(BlockType.SINGLES_STACK), StackState.isBlockTypeEnabled(BlockType.BAR_STACK)};
    }

    private static void restoreFlags(boolean[] flags) {
        StackState.setBlockEnabled(BlockType.STORAGE_STACK, flags[0]);
        StackState.setBlockEnabled(BlockType.SINGLES_STACK, flags[1]);
        StackState.setBlockEnabled(BlockType.BAR_STACK, flags[2]);
    }

    /**
     * No in-game reproduction applies: malformed server NBT must leave the displayed inventory
     * untouched rather than run saved-world recovery or retain rejected entries.
     */
    public static void clientInventoryUpdatesRejectMalformedDataAtomically(GameTestHelper helper) {
        for (StackBlockEntity be : List.of(GameTestScaffold.placeStorage(helper, ORIGIN),
                GameTestScaffold.placeSingles(helper, ORIGIN.east()),
                GameTestScaffold.placeBar(helper, ORIGIN.east(2)))) {
            be.getItems().setStackInSlot(0, new ItemStack(Items.STONE));
            CompoundTag valid = be.getUpdateTag(helper.getLevel().registryAccess());
            CompoundTag oversized = valid.copy();
            ListTag tooMany = new ListTag();
            CompoundTag item = valid.getCompound("Items").orElseThrow().getList("Items").orElseThrow().getCompound(0).orElseThrow();
            for (int i = 0; i <= be.getItems().getSlots(); i++) tooMany.add(item.copy());
            oversized.getCompound("Items").orElseThrow().put("Items", tooMany);
            rejectedUpdate(helper, be, oversized);

            CompoundTag duplicate = valid.copy();
            duplicate.getCompound("Items").orElseThrow().getList("Items").orElseThrow().add(item.copy());
            rejectedUpdate(helper, be, duplicate);
            for (int slot : new int[] {-1, be.getItems().getSlots()}) {
                CompoundTag outOfRange = valid.copy();
                outOfRange.getCompound("Items").orElseThrow().getList("Items").orElseThrow()
                        .getCompound(0).orElseThrow().putInt("Slot", slot);
                rejectedUpdate(helper, be, outOfRange);
            }
            CompoundTag missingSlot = valid.copy();
            missingSlot.getCompound("Items").orElseThrow().getList("Items").orElseThrow().getCompound(0).orElseThrow().remove("Slot");
            rejectedUpdate(helper, be, missingSlot);
            CompoundTag wrongSlotType = valid.copy();
            wrongSlotType.getCompound("Items").orElseThrow().getList("Items").orElseThrow()
                    .getCompound(0).orElseThrow().putByte("Slot", (byte) 0);
            rejectedUpdate(helper, be, wrongSlotType);
            CompoundTag mixedEntries = valid.copy();
            mixedEntries.getCompound("Items").orElseThrow().getList("Items").orElseThrow()
                    .add(StringTag.valueOf("not an item"));
            rejectedUpdate(helper, be, mixedEntries);
            CompoundTag invalidItem = valid.copy();
            invalidItem.getCompound("Items").orElseThrow().getList("Items").orElseThrow()
                    .getCompound(0).orElseThrow().putString("id", "somestacks:nonexistent_item");
            rejectedUpdate(helper, be, invalidItem);
            CompoundTag setAside = valid.copy();
            setAside.getCompound("Items").orElseThrow().put(StackItemStorage.TAG_SET_ASIDE, new ListTag());
            rejectedUpdate(helper, be, setAside);
            CompoundTag wrongType = valid.copy();
            wrongType.getCompound("Items").orElseThrow().putString("Items", "not a list");
            rejectedUpdate(helper, be, wrongType);
            CompoundTag wrongInventoryType = valid.copy();
            wrongInventoryType.putString("Items", "not a compound");
            rejectedUpdate(helper, be, wrongInventoryType);
            CompoundTag missingInventory = valid.copy();
            missingInventory.remove("Items");
            rejectedUpdate(helper, be, missingInventory);
            CompoundTag oldVersion = valid.copy();
            oldVersion.remove("DataVersion");
            rejectedUpdate(helper, be, oldVersion);
            CompoundTag wrongVersionType = valid.copy();
            wrongVersionType.putDouble("DataVersion", valid.getInt("DataVersion").orElseThrow());
            rejectedUpdate(helper, be, wrongVersionType);
            CompoundTag serverOnly = valid.copy();
            serverOnly.putBoolean("Permanent", true);
            rejectedUpdate(helper, be, serverOnly);

            CompoundTag replacement = valid.copy();
            var source = new StackItemStorage(be.getItems().getSlots());
            source.setStackInSlot(1, new ItemStack(Items.DIAMOND));
            replacement.put("Items", source.save(helper.getLevel().registryAccess()));
            check(be.loadClientUpdate(replacement, helper.getLevel().registryAccess()),
                    "Valid current-format update rejected");
            check(be.getItems().getStackInSlot(0).isEmpty(), "Old client slot survived replacement");
            checkEquals(Items.DIAMOND, be.getItems().getStackInSlot(1).getItem(), "Replacement client item");
        }
        helper.succeed();
    }

    /** No in-game reproduction applies: malformed rotation metadata invalidates the entire update. */
    public static void clientSinglesUpdatesValidateRotationBeforeInventory(GameTestHelper helper) {
        var be = GameTestScaffold.placeSingles(helper, ORIGIN);
        be.getItems().insertItem(0, new ItemStack(Items.STONE));
        CompoundTag valid = be.getUpdateTag(helper.getLevel().registryAccess());
        var replacement = new StackItemStorage(64);
        replacement.setStackInSlot(1, new ItemStack(Items.DIAMOND));
        CompoundTag changedItems = replacement.save(helper.getLevel().registryAccess());
        CompoundTag missingRotations = valid.copy();
        missingRotations.put("Items", changedItems.copy());
        missingRotations.remove("CubeRotations");
        rejectedUpdate(helper, be, missingRotations);
        CompoundTag wrongRotationType = valid.copy();
        wrongRotationType.put("Items", changedItems.copy());
        wrongRotationType.put("CubeRotations", new ListTag());
        rejectedUpdate(helper, be, wrongRotationType);
        for (int[] rotations : new int[][] {new int[1], new int[65]}) {
            CompoundTag invalid = valid.copy();
            invalid.put("Items", changedItems.copy());
            invalid.putIntArray("CubeRotations", rotations);
            rejectedUpdate(helper, be, invalid);
        }
        for (int rotation : new int[] {-1, 4}) {
            CompoundTag invalid = valid.copy();
            int[] rotations = new int[64];
            rotations[0] = rotation;
            invalid.put("Items", changedItems.copy());
            invalid.putIntArray("CubeRotations", rotations);
            rejectedUpdate(helper, be, invalid);
        }
        check(be.loadClientUpdate(valid, helper.getLevel().registryAccess()), "Valid rotations rejected");
        helper.succeed();
    }

    private static void rejectedUpdate(GameTestHelper helper, StackBlockEntity be, CompoundTag tag) {
        CompoundTag before = be.getUpdateTag(helper.getLevel().registryAccess());
        check(!be.loadClientUpdate(tag, helper.getLevel().registryAccess()), "Malformed update accepted");
        var registries = helper.getLevel().registryAccess();
        checkEquals(before, be.getUpdateTag(registries), "Rejected snapshot changed render state");
        checkEquals(Items.STONE, be.getItems().getStackInSlot(0).getItem(), "Rejected update changed slot 0");
        check(be.getItems().getStackInSlot(1).isEmpty(), "Rejected update partially changed inventory");
        check(!be.getItems().save(helper.getLevel().registryAccess()).contains("SetAside"),
                "Client retained rejected entries for recovery");
    }

    private static void rejects(Runnable operation, String message) {
        boolean rejected = false;
        try {
            operation.run();
        } catch (RuntimeException expected) {
            rejected = true;
        }
        check(rejected, message);
    }
}
