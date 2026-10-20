package com.github.crittscott.somestacks.gametest;

import com.github.crittscott.somestacks.ServerConfig;
import com.github.crittscott.somestacks.block.StackBlockEntity;
import com.github.crittscott.somestacks.client.ClientRenderToolState;
import com.github.crittscott.somestacks.client.ConfigSyncReceiver;
import com.github.crittscott.somestacks.client.StackState;
import com.github.crittscott.somestacks.network.ConfigSyncNetwork;
import com.github.crittscott.somestacks.network.ConfigSyncPkt;
import com.github.crittscott.somestacks.util.BlockType;
import com.github.crittscott.somestacks.util.StackItemStorage;
import io.netty.buffer.Unpooled;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.storage.TagValueInput;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

import static com.github.crittscott.somestacks.gametest.GameTestScaffold.ORIGIN;
import static com.github.crittscott.somestacks.gametest.GameTestScaffold.check;
import static com.github.crittscott.somestacks.gametest.GameTestScaffold.checkEquals;

/** Wire boundaries, independent of forgiving saved-world recovery. */
public final class SynchronizationChecks {
    private SynchronizationChecks() {}

    /**
     * Complete snapshots publish flags and both lists only at their final chunk. To reproduce
     * in-game: configure disabled types and namespace lists, join, and inspect mode cycling and
     * /ss write list. A large list exercises multiple packets; exact atomicity requires this test.
     */
    public static void configSnapshotsRoundTripAndPublishAtomically(GameTestHelper helper) {
        var policy = ServerConfig.settings();
        List<String> originalGallery = ClientRenderToolState.genMods();
        List<String> originalDenied = List.copyOf(ClientRenderToolState.disabledMods());
        boolean[] originalFlags = flags();
        ConfigSyncReceiver.clear();
        try {
            for (int size : new int[] {0, 1, 4096}) {
                List<String> gallery = new ArrayList<>();
                List<String> denied = new ArrayList<>();
                for (int i = 0; i < size; i++) {
                    gallery.add("gallery" + i + "a".repeat(240));
                    denied.add("denied" + i + "b".repeat(240));
                }
                boolean[] expectedFlags = {size == 4096, size != 4096, size == 0};
                ServerConfig.apply(new ServerConfig.Settings(policy.maxPileHeight(),
                        expectedFlags[0], expectedFlags[1], expectedFlags[2],
                        denied, policy.disableItems(), policy.renderGalleryPlacementsPerTick(),
                        policy.galleryEnabled(), policy.galleryPermissionLevel(), gallery, policy.genItems()));
                ConfigSyncPkt.rebuildCurrent();
                List<ConfigSyncPkt> packets = ConfigSyncPkt.currentPackets();
                check(size == 4096 ? packets.size() > 1 : packets.size() == 1, "Wrong chunking");
                List<ConfigSyncPkt> sent = new ArrayList<>();
                ConfigSyncNetwork.sendCurrent(sent::add);
                checkEquals(packets, sent, "Delivery skipped/reordered cached chunks");
                List<String> beforeGallery = ClientRenderToolState.genMods();
                var beforeDenied = ClientRenderToolState.disabledMods();
                boolean[] beforeFlags = flags();
                int bytes = 0;
                for (int i = 0; i < sent.size(); i++) {
                    ConfigSyncPkt packet = sent.get(i);
                    checkEquals(i == 0, packet.first(), "First marker");
                    checkEquals(i == sent.size() - 1, packet.last(), "Last marker");
                    checkEquals(packets.get(0).generation(), packet.generation(), "Generation changed");
                    bytes += packet.budgetBytes();
                    var raw = Unpooled.buffer();
                    try {
                        var buf = new RegistryFriendlyByteBuf(raw, helper.getLevel().registryAccess());
                        ConfigSyncPkt.STREAM_CODEC.encode(buf, packet);
                        check(raw.readableBytes() <= ConfigSyncPkt.MAX_PACKET_BYTES, "Encoded chunk too large");
                        ConfigSyncPkt decoded = ConfigSyncPkt.STREAM_CODEC.decode(buf);
                        checkEquals(packet, decoded, "Chunk codec round trip");
                        ConfigSyncReceiver.apply(decoded);
                    } finally {
                        raw.release();
                    }
                    if (!packet.last()) {
                        checkEquals(beforeGallery, ClientRenderToolState.genMods(), "Gallery published early");
                        checkEquals(beforeDenied, ClientRenderToolState.disabledMods(), "Deny list published early");
                        check(Arrays.equals(beforeFlags, flags()), "Flags published early");
                    }
                }
                check(packets.size() <= ConfigSyncPkt.MAX_GENERATION_CHUNKS, "Chunk budget");
                check(bytes <= ConfigSyncPkt.MAX_GENERATION_BYTES, "Generation byte budget");
                checkEquals(gallery, ClientRenderToolState.genMods(), "Published gallery list");
                checkEquals(Set.copyOf(denied), ClientRenderToolState.disabledMods(), "Published deny set");
                check(Arrays.equals(expectedFlags, flags()), "Published flags");
            }
        } finally {
            ServerConfig.apply(policy);
            ConfigSyncPkt.rebuildCurrent();
            ConfigSyncReceiver.clear();
            ClientRenderToolState.replace(originalGallery, originalDenied);
            restoreFlags(originalFlags);
        }
        helper.succeed();
    }

    /**
     * Missing, inconsistent, and superseded generations cannot publish partial settings. No
     * in-game reproduction applies: these are synthetic interrupted and malformed sequences.
     */
    public static void configStagingChecksSequencesAndTotals(GameTestHelper helper) {
        var originalGallery = ClientRenderToolState.genMods();
        var originalDenied = List.copyOf(ClientRenderToolState.disabledMods());
        boolean[] originalFlags = flags();
        ConfigSyncReceiver.clear();
        try {
            rejects(() -> ConfigSyncReceiver.apply(chunk(1, false, true, 1, "tail")), "Missing first accepted");
            ConfigSyncReceiver.apply(chunk(2, true, false, 2, "old"));
            checkEquals(originalGallery, ClientRenderToolState.genMods(), "Missing last published");
            ConfigSyncReceiver.apply(chunk(3, true, true, 1, "replacement"));
            checkEquals(List.of("replacement"), ClientRenderToolState.genMods(), "New first did not replace staging");
            rejects(() -> ConfigSyncReceiver.apply(chunk(2, false, true, 2, "old_tail")), "Old tail accepted");
            ConfigSyncReceiver.apply(chunk(4, true, false, 2, "first"));
            rejects(() -> ConfigSyncReceiver.apply(chunk(5, false, true, 2, "tail")), "Mismatched generation accepted");
            for (ConfigSyncPkt changed : List.of(
                    new ConfigSyncPkt(6, false, true, false, true, true, 2, 0, List.of("tail"), List.of()),
                    new ConfigSyncPkt(6, false, true, true, false, true, 2, 0, List.of("tail"), List.of()),
                    new ConfigSyncPkt(6, false, true, true, true, false, 2, 0, List.of("tail"), List.of()),
                    chunk(6, false, true, 3, "tail"),
                    new ConfigSyncPkt(6, false, true, true, true, true, 2, 1, List.of("tail"), List.of()))) {
                ConfigSyncReceiver.apply(chunk(6, true, false, 2, "first"));
                rejects(() -> ConfigSyncReceiver.apply(changed), "Changed metadata accepted");
                rejects(() -> ConfigSyncReceiver.apply(chunk(6, false, true, 2, "tail")), "Rejected staging survived");
            }
            rejects(() -> ConfigSyncReceiver.apply(chunk(7, true, true, 2, "only")), "Total underflow accepted");
            ConfigSyncReceiver.apply(chunk(8, true, false, 1, "first"));
            rejects(() -> ConfigSyncReceiver.apply(chunk(8, false, true, 1, "extra")), "Total overflow accepted");
            ConfigSyncReceiver.apply(new ConfigSyncPkt(9, true, false, true, true, true,
                    0, 2, List.of(), List.of("denied")));
            rejects(() -> ConfigSyncReceiver.apply(new ConfigSyncPkt(9, false, true, true, true, true,
                    0, 2, List.of(), List.of("denied"))), "Duplicate deny entry accepted");
            checkEquals(List.of("replacement"), ClientRenderToolState.genMods(), "Bad sequence replaced published state");
            check(Arrays.equals(new boolean[] {true, true, true}, flags()), "Bad sequence changed flags");
            check(ClientRenderToolState.disabledMods().isEmpty(), "Bad sequence changed deny set");
            ConfigSyncReceiver.apply(chunk(10, true, true, 1, "recovered"));
            checkEquals(List.of("recovered"), ClientRenderToolState.genMods(), "Valid snapshot after rejection failed");
        } finally {
            ConfigSyncReceiver.clear();
            ClientRenderToolState.replace(originalGallery, originalDenied);
            restoreFlags(originalFlags);
        }
        helper.succeed();
    }

    /** No in-game reproduction applies: invalid wire counts and namespace encodings must fail decoding. */
    public static void configDecoderRejectsMalformedCountsAndNamespaces(GameTestHelper helper) {
        for (int[] counts : new int[][] {{-1, 0, 0, 0}, {4097, 0, 0, 0},
                {0, -1, 0, 0}, {0, 4097, 0, 0}, {1, 0, -1, 0}, {1, 0, 2, 0},
                {0, 1, 0, -1}, {0, 1, 0, 2}}) {
            var raw = Unpooled.buffer();
            try {
                var buf = new RegistryFriendlyByteBuf(raw, helper.getLevel().registryAccess());
                writeHeader(buf, 1, counts[0], counts[1]);
                buf.writeVarInt(counts[2]);
                buf.writeVarInt(counts[3]);
                rejects(() -> ConfigSyncPkt.STREAM_CODEC.decode(buf), "Invalid wire count decoded");
            } finally {
                raw.release();
            }
        }
        for (int generation : new int[] {0, -1}) {
            var raw = Unpooled.buffer();
            try {
                var buf = new RegistryFriendlyByteBuf(raw, helper.getLevel().registryAccess());
                writeHeader(buf, generation, 0, 0);
                buf.writeVarInt(0);
                buf.writeVarInt(0);
                rejects(() -> ConfigSyncPkt.STREAM_CODEC.decode(buf), "Invalid generation decoded");
            } finally {
                raw.release();
            }
        }
        for (String namespace : List.of("", "Uppercase", "bad namespace", "a".repeat(257))) {
            var raw = Unpooled.buffer();
            try {
                var buf = new RegistryFriendlyByteBuf(raw, helper.getLevel().registryAccess());
                writeHeader(buf, 1, 1, 0);
                buf.writeVarInt(1);
                buf.writeUtf(namespace);
                buf.writeVarInt(0);
                rejects(() -> ConfigSyncPkt.STREAM_CODEC.decode(buf), "Invalid namespace decoded");
            } finally {
                raw.release();
            }
        }
        helper.succeed();
    }

    private static void writeHeader(RegistryFriendlyByteBuf buf, int generation, int gallery, int denied) {
        buf.writeVarInt(generation);
        for (int i = 0; i < 5; i++) buf.writeBoolean(true);
        buf.writeVarInt(gallery);
        buf.writeVarInt(denied);
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

    /** No in-game reproduction applies: an oversized server payload is rejected before decoding. */
    public static void configDecoderEnforcesPacketBudget(GameTestHelper helper) {
        var raw = Unpooled.buffer();
        var buf = new RegistryFriendlyByteBuf(raw, helper.getLevel().registryAccess());
        try {
            raw.writeZero(ConfigSyncPkt.MAX_PACKET_BYTES + 1);
            rejects(() -> ConfigSyncPkt.STREAM_CODEC.decode(buf), "Oversized config packet decoded");
            checkEquals(0, raw.readerIndex(), "Oversized packet was read before rejection");
            raw.clear();
            var packet = new ConfigSyncPkt(1, true, true, true, false, true,
                    2, 1, List.of("minecraft", "somestacks"), List.of("denied"));
            ConfigSyncPkt.STREAM_CODEC.encode(buf, packet);
            checkEquals(packet, ConfigSyncPkt.STREAM_CODEC.decode(buf), "Bounded config round trip");
        } finally {
            raw.release();
        }
        helper.succeed();
    }

    /**
     * No in-game reproduction applies: malicious chunk sequences must clear staging without
     * publishing an incomplete snapshot; an honest client cannot construct these sequences.
     */
    public static void configStagingRejectsAndClearsInvalidGenerations(GameTestHelper helper) {
        List<String> published = ClientRenderToolState.genMods();
        ConfigSyncReceiver.clear();
        try {
            ConfigSyncReceiver.apply(chunk(1, true, false, 10, "first"));
            checkEquals(published, ClientRenderToolState.genMods(), "Incomplete snapshot published");
            rejects(() -> ConfigSyncReceiver.apply(chunk(1, false, false, 10)),
                    "Empty intermediate chunk accepted");
            rejects(() -> ConfigSyncReceiver.apply(chunk(1, false, true, 10, "second")),
                    "Rejected generation retained staging");
            ConfigSyncReceiver.apply(chunk(2, true, false, 10, "first"));
            rejects(() -> ConfigSyncReceiver.apply(chunk(2, false, true, 10, "first")),
                    "Duplicate namespace accepted");
            for (int i = 0; i < ConfigSyncPkt.MAX_GENERATION_CHUNKS; i++) {
                ConfigSyncReceiver.apply(chunk(3, i == 0, false, 4096, "mod" + i));
            }
            rejects(() -> ConfigSyncReceiver.apply(chunk(3, false, false, 4096, "overflow")),
                    "Generation chunk budget was ignored");
            rejects(() -> ConfigSyncReceiver.apply(chunk(3, false, true, 4096, "tail")),
                    "Over-budget generation retained staging");
            checkEquals(published, ClientRenderToolState.genMods(), "Rejected snapshot published");
        } finally {
            ConfigSyncReceiver.clear();
        }
        helper.succeed();
    }

    private static ConfigSyncPkt chunk(int generation, boolean first, boolean last, int total,
                                       String... namespaces) {
        return new ConfigSyncPkt(generation, first, last, true, true, true,
                total, 0, List.of(namespaces), List.of());
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
            var registries = helper.getLevel().registryAccess();
            check(be.isValidClientUpdate(TagValueInput.create(ProblemReporter.DISCARDING, registries, valid)),
                    "Valid client preflight was rejected");
            check(be.loadClientUpdate(valid, registries), "Valid shared replacement was rejected");
            checkEquals(Items.STONE, be.getItems().getStackInSlot(0).getItem(), "Shared initial replacement item");
            check(be.loadClientUpdate(replacement, registries), "Valid later replacement was rejected");
            checkEquals(Items.DIAMOND, be.getItems().getStackInSlot(1).getItem(), "Later shared replacement item");
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
        check(!be.isValidClientUpdate(TagValueInput.create(ProblemReporter.DISCARDING, registries, tag)),
                "Malformed client preflight was accepted");
        checkEquals(before, be.getUpdateTag(registries), "Rejected preflight changed render state");
        check(!be.loadClientUpdate(tag, registries), "Malformed shared replacement was accepted");
        checkEquals(before, be.getUpdateTag(registries), "Rejected replacement changed render state");
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
