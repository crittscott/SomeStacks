package com.github.crittscott.somestacks.gametest;

import static com.github.crittscott.somestacks.gametest.GameTestScaffold.check;
import static com.github.crittscott.somestacks.gametest.GameTestScaffold.checkEquals;
import static com.github.crittscott.somestacks.gametest.GameTestScaffold.policy;
import static com.github.crittscott.somestacks.util.BlockType.BAR_STACK;
import static com.github.crittscott.somestacks.util.BlockType.SINGLES_STACK;
import static com.github.crittscott.somestacks.util.BlockType.STORAGE_STACK;

import com.github.crittscott.somestacks.ServerConfig;
import com.github.crittscott.somestacks.client.ClientServerSettings;
import com.github.crittscott.somestacks.network.ConfigSyncPkt;
import com.github.crittscott.somestacks.network.GestureStatePkt;
import com.github.crittscott.somestacks.util.BlockType;
import com.github.crittscott.somestacks.util.StackMode;
import com.mojang.brigadier.exceptions.CommandSyntaxException;

import io.netty.buffer.Unpooled;
import io.netty.handler.codec.DecoderException;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;
import java.util.stream.IntStream;

/** Policy publication and the complete snapshot's trust boundary, shared by both loaders. */
public final class NetworkPolicyChecks {
    private NetworkPolicyChecks() {}

    /**
     * To reproduce in-game: change pile height or the exact-item deny list, then change a stack
     * enable flag or a gallery/mod-deny namespace. Only the latter changes synchronize client
     * policy. Repeating /ss reload with unchanged values sends no policy packet. The callback
     * counts here distinguish an absent update from an update whose contents look unchanged.
     */
    public static void publicationSyncsOnlyChangedClientPolicy(GameTestHelper helper)
            throws ReflectiveOperationException {
        ServerConfig.Settings before = ServerConfig.settings();
        Field field = ServerConfig.class.getDeclaredField("syncClients");
        field.setAccessible(true);
        Object previousCallback = field.get(null);
        List<ConfigSyncPkt> sent = new ArrayList<>();
        field.set(
                null,
                (Runnable)
                        () -> {
                            ConfigSyncPkt snapshot =
                                    ConfigSyncPkt.snapshot(ServerConfig.settings());
                            checkEquals(
                                    snapshot.disabledMods().contains("policy_marker"),
                                    ServerConfig.isModDisabled("policy_marker"),
                                    "Synchronization preceded admission publication");
                            sent.add(snapshot);
                        });
        try {
            ServerConfig.publish(before);
            checkEquals(0, sent.size(), "Unchanged policy synchronized");
            ServerConfig.Settings serverOnly =
                    policy(
                            before,
                            Map.of(
                                    ServerConfig.Setting.MAX_PILE_HEIGHT,
                                    before.maxPileHeight() == 1 ? 2 : 1,
                                    ServerConfig.Setting.DISABLE_ITEMS,
                                    List.of("minecraft:stone")));
            ServerConfig.publish(serverOnly);
            checkEquals(0, sent.size(), "Server-only policy synchronized");
            ServerConfig.Settings flags =
                    policy(
                            serverOnly,
                            Map.of(
                                    ServerConfig.Setting.ENABLE_STORAGE_STACK_BLOCK,
                                    !before.enableStorageStackBlock()));
            ServerConfig.publish(flags);
            checkEquals(1, sent.size(), "Flag change did not synchronize once");
            ServerConfig.publish(flags);
            checkEquals(1, sent.size(), "Repeated publication synchronized");
            ServerConfig.publish(
                    withNamespaces(flags, List.of("policy_marker"), List.of("gallery_marker")));
            checkEquals(2, sent.size(), "Namespace changes did not synchronize once");
            List<String> oversized =
                    IntStream.rangeClosed(0, ServerConfig.MAX_NAMESPACE_ENTRIES)
                            .mapToObj(i -> "namespace_" + i)
                            .toList();
            check(
                    !ServerConfig.publish(withNamespaces(flags, oversized, List.of())),
                    "Oversize policy accepted");
            checkEquals(2, sent.size(), "Rejected policy synchronized");
        } finally {
            field.set(null, previousCallback);
            ServerConfig.publish(before);
        }
        helper.succeed();
    }

    /**
     * To reproduce in-game: deny minecraft:stone by item id; players cannot deposit it, but hoppers
     * can. Deny the minecraft mod and both paths refuse it. Existing contents remain extractable.
     * Bar-tagged items go to Bar and Storage, never Singles, including when the exact-item deny
     * list excludes them only for players. This fixture also checks empty inputs for every type.
     */
    public static void admissionSeparatesPlayersAutomationAndTypes(
            GameTestHelper helper, ServerPlayer player) {
        ServerConfig.Settings before = ServerConfig.settings();
        try {
            ServerConfig.Settings pinned =
                    policy(
                            before,
                            Map.of(
                                    ServerConfig.Setting.ENABLE_STORAGE_STACK_BLOCK, true,
                                    ServerConfig.Setting.ENABLE_SINGLES_STACK_BLOCK, true,
                                    ServerConfig.Setting.ENABLE_BAR_STACK_BLOCK, true,
                                    ServerConfig.Setting.DISABLE_MODS, List.of(),
                                    ServerConfig.Setting.DISABLE_ITEMS,
                                            List.of("minecraft:stone")));
            ServerConfig.publish(pinned);
            ItemStack stone = new ItemStack(Items.STONE);
            check(STORAGE_STACK.admits(stone, null), "Exact-item denial reached automation");
            check(!STORAGE_STACK.admits(stone, player), "Player bypassed exact-item denial");
            check(
                    !BlockType.permitsPlayerItemAndNotify(stone, player),
                    "Player policy gate ignored denial");
            for (BlockType type : BlockType.values()) {
                check(!type.admits(ItemStack.EMPTY, null), "Empty automation input accepted");
                check(!type.admits(ItemStack.EMPTY, player), "Empty player input accepted");
            }
            ItemStack bar = new ItemStack(GameTestScaffold.firstBarItem());
            check(BAR_STACK.admits(bar, null), "Tagged bar refused");
            check(STORAGE_STACK.admits(bar, null), "Storage refused tagged bar");
            check(!SINGLES_STACK.admits(bar, null), "Singles accepted tagged bar");
            pinned =
                    policy(
                            pinned,
                            Map.of(
                                    ServerConfig.Setting.DISABLE_ITEMS,
                                    List.of(
                                            "minecraft:stone",
                                            BuiltInRegistries.ITEM
                                                    .getKey(bar.getItem())
                                                    .toString())));
            ServerConfig.publish(pinned);
            check(!BAR_STACK.admits(bar, player), "Player bypassed tagged-item denial");
            check(BAR_STACK.admits(bar, null), "Tagged-item denial reached automation");
            check(!SINGLES_STACK.admits(bar, null), "Player-only denial changed type eligibility");
            ServerConfig.publish(withNamespaces(pinned, List.of("minecraft"), pinned.genMods()));
            for (BlockType type : BlockType.values()) {
                check(!type.admits(stone, null), "Mod denial missed automation");
                check(!type.admits(stone, player), "Mod denial missed player");
            }
        } finally {
            ServerConfig.publish(before);
        }
        helper.succeed();
    }

    /**
     * To reproduce in-game: put mixed-case, duplicate, and malformed namespaces in gen_mods and
     * disable_mods, then reload and inspect both lists. Valid names are lowercased and unique.
     * Reload a list containing 257 distinct names or a valid 257-character name: the previous
     * complete policy stays active. Adding a 257th namespace by command fails without saving it.
     */
    public static void namespacePolicyIsPreparedAndBounded(GameTestHelper helper) {
        ServerConfig.Settings before = ServerConfig.settings();
        MinecraftServer server = helper.getLevel().getServer();
        try {
            check(
                    ServerConfig.publish(
                            withNamespaces(
                                    before,
                                    List.of(" Minecraft ", "minecraft", "bad/name"),
                                    List.of(" EXAMPLE ", "example", "bad:name", ""))),
                    "Valid policy refused");
            checkEquals(
                    List.of("minecraft"),
                    ServerConfig.settings().disableMods(),
                    "Deny normalization");
            checkEquals(
                    List.of("example"), ServerConfig.settings().genMods(), "Gallery normalization");
            check(
                    ServerConfig.isModDisabled("minecraft"),
                    "Admission differs from published policy");

            List<String> full =
                    IntStream.range(0, ServerConfig.MAX_NAMESPACE_ENTRIES)
                            .mapToObj(i -> "policy_" + i)
                            .toList();
            check(
                    ServerConfig.publish(withNamespaces(before, full, full)),
                    "Boundary policy refused");
            ServerConfig.Settings accepted = ServerConfig.settings();
            List<String> tooMany =
                    IntStream.rangeClosed(0, ServerConfig.MAX_NAMESPACE_ENTRIES)
                            .mapToObj(i -> "policy_" + i)
                            .toList();
            check(
                    !ServerConfig.publish(withNamespaces(before, tooMany, List.of())),
                    "Oversize deny accepted");
            check(
                    !ServerConfig.publish(withNamespaces(before, List.of(), tooMany)),
                    "Oversize gallery accepted");
            check(
                    !ServerConfig.publish(
                            withNamespaces(
                                    before,
                                    List.of("a".repeat(ServerConfig.MAX_NAMESPACE_LENGTH + 1)),
                                    List.of())),
                    "Overlong namespace accepted");
            check(ServerConfig.settings() == accepted, "Rejection replaced previous policy");
            check(ServerConfig.isModDisabled("policy_0"), "Rejected policy changed admission");

            for (String list : List.of("gen", "deny")) {
                int result =
                        server.getCommands()
                                .getDispatcher()
                                .execute(
                                        "ss " + list + " mod add extra",
                                        server.createCommandSourceStack().withSuppressedOutput());
                checkEquals(0, result, "Full-list command reported success");
            }
            check(ServerConfig.settings() == accepted, "Rejected command changed policy");
        } catch (CommandSyntaxException exception) {
            throw new AssertionError("Namespace command failed to parse", exception);
        } finally {
            ServerConfig.publish(before);
        }
        helper.succeed();
    }

    /**
     * To reproduce in-game: disable Storage and Bar, enable Singles, and reload; mode cycling skips
     * the disabled types while /ss write list uses the new gallery list. Reconnect to a server with
     * different lists and enable flags to see the whole snapshot replaced. This fixture also
     * injects malformed wire data and checks the maximum legal payload, which ordinary UI cannot
     * send. Applying a snapshot here may also move the client's selected mode off a disabled type;
     * {@link ClientGestureChecks} owns those mode assertions.
     */
    public static void policyPacketsValidateBeforePublication(GameTestHelper helper) {
        ConfigSyncPkt previous = ClientServerSettings.snapshot();
        try {
            ConfigSyncPkt changed =
                    new ConfigSyncPkt(false, true, false, List.of("gallery"), List.of("denied"));
            ClientServerSettings.apply(roundTrip(changed), null);
            check(
                    !ClientServerSettings.isBlockTypeEnabled(STORAGE_STACK),
                    "Storage flag not applied");
            check(
                    ClientServerSettings.isBlockTypeEnabled(SINGLES_STACK),
                    "Singles flag not applied");
            check(!ClientServerSettings.isBlockTypeEnabled(BAR_STACK), "Bar flag not applied");
            checkEquals(
                    List.of("gallery"), ClientServerSettings.genMods(), "Gallery list not applied");
            check(ClientServerSettings.isDisabled("denied"), "Deny list not applied");

            List<String> maximum =
                    IntStream.range(0, ServerConfig.MAX_NAMESPACE_ENTRIES)
                            .mapToObj(i ->
                                    "a".repeat(ServerConfig.MAX_NAMESPACE_LENGTH - 3)
                                            + String.format(Locale.ROOT, "%03d", i))
                            .toList();
            ConfigSyncPkt boundary = new ConfigSyncPkt(true, false, true, maximum, maximum);
            checkEquals(boundary, roundTrip(boundary), "Maximum packet did not round trip");
            checkEquals(
                    new ConfigSyncPkt(true, true, true, List.of(), List.of()),
                    roundTrip(new ConfigSyncPkt(true, true, true, List.of(), List.of())),
                    "Empty lists");

            reject(buf -> buf.writeVarInt(-1));
            reject(buf -> buf.writeVarInt(ServerConfig.MAX_NAMESPACE_ENTRIES + 1));
            reject(buf -> buf.writeVarInt(0).writeVarInt(ServerConfig.MAX_NAMESPACE_ENTRIES + 1));
            for (String invalid :
                    List.of(
                            "",
                            "Uppercase",
                            "bad/name",
                            "bad:name",
                            "a".repeat(ServerConfig.MAX_NAMESPACE_LENGTH + 1))) {
                reject(buf -> buf.writeVarInt(1).writeUtf(invalid).writeVarInt(0));
                reject(buf -> buf.writeVarInt(0).writeVarInt(1).writeUtf(invalid));
            }
            reject(buf ->
                    buf.writeVarInt(2).writeUtf("duplicate").writeUtf("duplicate").writeVarInt(0));
            reject(buf ->
                    buf.writeVarInt(0).writeVarInt(2).writeUtf("duplicate").writeUtf("duplicate"));
            reject(buf -> buf.writeVarInt(1));
            reject(buf -> buf.writeVarInt(0).writeVarInt(0).writeByte(0));
            checkEquals(
                    List.of("gallery"),
                    ClientServerSettings.genMods(),
                    "Rejected data changed lists");

            ClientServerSettings.apply(
                    new ConfigSyncPkt(true, true, true, List.of(), List.of()), null);
            check(ClientServerSettings.genMods().isEmpty(), "Empty snapshot retained gallery list");
            check(
                    ClientServerSettings.snapshot().disabledMods().isEmpty(),
                    "Empty snapshot retained deny list");
        } finally {
            ClientServerSettings.apply(previous, null);
        }
        helper.succeed();
    }

    /**
     * No in-game form: an unmodified client only sends modes it can select. This guards the one
     * input the server takes from a player's client: every mode survives the trip under its fixed
     * wire id, and an unknown id or a truncated packet is rejected on the network thread before any
     * gesture state changes. The id table is written out here rather than read from {@link
     * StackMode}, so reordering the enum cannot change it unnoticed.
     */
    public static void gestureStatePacketValidatesOnDecode(GameTestHelper helper) {
        Map<StackMode, Integer> wireIds =
                Map.of(
                        StackMode.STORAGE_STACK, 0,
                        StackMode.SINGLES_STACK, 1,
                        StackMode.BAR_STACK, 2,
                        StackMode.TOGGLE_PERMANENT, 3);
        checkEquals(StackMode.values().length, wireIds.size(), "Mode without a wire id");
        for (StackMode mode : StackMode.values()) {
            for (boolean modifier : new boolean[] {false, true}) {
                GestureStatePkt packet = new GestureStatePkt(mode, modifier);
                FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
                try {
                    GestureStatePkt.encode(packet, buf);
                    checkEquals(
                            (int) wireIds.get(mode),
                            (int) buf.getUnsignedByte(buf.readerIndex()),
                            "Wire id of " + mode);
                    checkEquals(packet, GestureStatePkt.decode(buf), "Round trip of " + packet);
                    check(!buf.isReadable(), "Trailing gesture data for " + packet);
                } finally {
                    buf.release();
                }
            }
        }
        rejectGesture(buf -> buf.writeByte(wireIds.size()).writeBoolean(false));
        rejectGesture(buf -> buf.writeByte(255).writeBoolean(true));
        rejectGesture(buf -> buf.writeByte(0));
        helper.succeed();
    }

    private static void rejectGesture(Consumer<FriendlyByteBuf> write) {
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        try {
            write.accept(buf);
            try {
                GestureStatePkt.decode(buf);
                throw new AssertionError("Malformed gesture packet accepted");
            } catch (DecoderException | IndexOutOfBoundsException expected) {
                // The network-thread codec rejects malformed data before server state changes.
            }
        } finally {
            buf.release();
        }
    }

    private static ConfigSyncPkt roundTrip(ConfigSyncPkt packet) {
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        try {
            ConfigSyncPkt.encode(packet, buf);
            return ConfigSyncPkt.decode(buf);
        } finally {
            buf.release();
        }
    }

    private static void reject(Consumer<FriendlyByteBuf> write) {
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        try {
            buf.writeZero(3);
            write.accept(buf);
            try {
                ConfigSyncPkt.decode(buf);
                throw new AssertionError("Malformed policy packet accepted");
            } catch (DecoderException | IndexOutOfBoundsException expected) {
                // The network-thread codec rejects malformed data before client publication.
            }
        } finally {
            buf.release();
        }
    }

    private static ServerConfig.Settings withNamespaces(
            ServerConfig.Settings source, List<String> disabled, List<String> gallery) {
        return policy(
                source,
                Map.of(
                        ServerConfig.Setting.DISABLE_MODS, disabled,
                        ServerConfig.Setting.GEN_MODS, gallery));
    }
}
