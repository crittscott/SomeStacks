package com.github.crittscott.somestacks.gametest;

import com.github.crittscott.somestacks.block.StackBlockEntity;
import com.github.crittscott.somestacks.client.ClientRenderPacketSink;
import com.github.crittscott.somestacks.client.ClientRenderToolState;
import com.github.crittscott.somestacks.network.ConfigSyncPkt;
import com.github.crittscott.somestacks.util.StackItemStorage;
import io.netty.buffer.Unpooled;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.List;

import static com.github.crittscott.somestacks.gametest.GameTestScaffold.ORIGIN;
import static com.github.crittscott.somestacks.gametest.GameTestScaffold.check;
import static com.github.crittscott.somestacks.gametest.GameTestScaffold.checkEquals;

/** Wire boundaries, independent of forgiving saved-world recovery. */
public final class SynchronizationChecks {
    private SynchronizationChecks() {}

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
        ClientRenderPacketSink.clear();
        try {
            ClientRenderPacketSink.apply(chunk(1, true, false, 10, "first"));
            checkEquals(published, ClientRenderToolState.genMods(), "Incomplete snapshot published");
            rejects(() -> ClientRenderPacketSink.apply(chunk(1, false, false, 10)),
                    "Empty intermediate chunk accepted");
            rejects(() -> ClientRenderPacketSink.apply(chunk(1, false, true, 10, "second")),
                    "Rejected generation retained staging");
            ClientRenderPacketSink.apply(chunk(2, true, false, 10, "first"));
            rejects(() -> ClientRenderPacketSink.apply(chunk(2, false, true, 10, "first")),
                    "Duplicate namespace accepted");
            for (int i = 0; i < ConfigSyncPkt.MAX_GENERATION_CHUNKS; i++) {
                ClientRenderPacketSink.apply(chunk(3, i == 0, false, 4096, "mod" + i));
            }
            rejects(() -> ClientRenderPacketSink.apply(chunk(3, false, false, 4096, "overflow")),
                    "Generation chunk budget was ignored");
            rejects(() -> ClientRenderPacketSink.apply(chunk(3, false, true, 4096, "tail")),
                    "Over-budget generation retained staging");
            checkEquals(published, ClientRenderToolState.genMods(), "Rejected snapshot published");
        } finally {
            ClientRenderPacketSink.clear();
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
            CompoundTag item = valid.getCompound("Items").getList("Items", Tag.TAG_COMPOUND).getCompound(0);
            for (int i = 0; i <= be.getItems().getSlots(); i++) tooMany.add(item.copy());
            oversized.getCompound("Items").put("Items", tooMany);
            rejectedUpdate(helper, be, oversized);

            CompoundTag duplicate = valid.copy();
            duplicate.getCompound("Items").getList("Items", Tag.TAG_COMPOUND).add(item.copy());
            rejectedUpdate(helper, be, duplicate);
            for (int slot : new int[] {-1, be.getItems().getSlots()}) {
                CompoundTag outOfRange = valid.copy();
                outOfRange.getCompound("Items").getList("Items", Tag.TAG_COMPOUND)
                        .getCompound(0).putInt("Slot", slot);
                rejectedUpdate(helper, be, outOfRange);
            }
            CompoundTag missingSlot = valid.copy();
            missingSlot.getCompound("Items").getList("Items", Tag.TAG_COMPOUND).getCompound(0).remove("Slot");
            rejectedUpdate(helper, be, missingSlot);
            CompoundTag invalidItem = valid.copy();
            invalidItem.getCompound("Items").getList("Items", Tag.TAG_COMPOUND)
                    .getCompound(0).putString("id", "somestacks:nonexistent_item");
            rejectedUpdate(helper, be, invalidItem);
            CompoundTag setAside = valid.copy();
            setAside.getCompound("Items").put(StackItemStorage.TAG_SET_ASIDE, new ListTag());
            rejectedUpdate(helper, be, setAside);
            CompoundTag wrongType = valid.copy();
            wrongType.getCompound("Items").putString("Items", "not a list");
            rejectedUpdate(helper, be, wrongType);
            CompoundTag oldVersion = valid.copy();
            oldVersion.remove("DataVersion");
            rejectedUpdate(helper, be, oldVersion);
            CompoundTag serverOnly = valid.copy();
            serverOnly.putBoolean("Permanent", true);
            rejectedUpdate(helper, be, serverOnly);

            CompoundTag replacement = valid.copy();
            var source = new StackItemStorage(be.getItems().getSlots());
            source.setStackInSlot(1, new ItemStack(Items.DIAMOND));
            replacement.put("Items", source.serializeNBT(helper.getLevel().registryAccess()));
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
        be.getItems().insertItem(0, new ItemStack(Items.STONE), false);
        CompoundTag valid = be.getUpdateTag(helper.getLevel().registryAccess());
        var replacement = new StackItemStorage(64);
        replacement.setStackInSlot(1, new ItemStack(Items.DIAMOND));
        CompoundTag changedItems = replacement.serializeNBT(helper.getLevel().registryAccess());
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
        check(!be.loadClientUpdate(tag, helper.getLevel().registryAccess()), "Malformed update accepted");
        checkEquals(Items.STONE, be.getItems().getStackInSlot(0).getItem(), "Rejected update changed slot 0");
        check(be.getItems().getStackInSlot(1).isEmpty(), "Rejected update partially changed inventory");
        check(!be.getItems().serializeNBT(helper.getLevel().registryAccess()).contains("SetAside"),
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
