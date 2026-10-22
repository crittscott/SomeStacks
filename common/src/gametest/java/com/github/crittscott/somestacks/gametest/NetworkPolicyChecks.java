package com.github.crittscott.somestacks.gametest;

import com.github.crittscott.somestacks.ServerConfig;
import com.github.crittscott.somestacks.client.ClientServerSettings;
import com.github.crittscott.somestacks.network.ConfigSyncPkt;
import com.github.crittscott.somestacks.util.BlockType;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import io.netty.buffer.Unpooled;
import io.netty.handler.codec.DecoderException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.IntStream;

import static com.github.crittscott.somestacks.gametest.GameTestScaffold.check;
import static com.github.crittscott.somestacks.gametest.GameTestScaffold.checkEquals;
import static com.github.crittscott.somestacks.util.BlockType.BAR_STACK;
import static com.github.crittscott.somestacks.util.BlockType.SINGLES_STACK;
import static com.github.crittscott.somestacks.util.BlockType.STORAGE_STACK;

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
        field.set(null, (Runnable) () -> {
            ConfigSyncPkt snapshot = ConfigSyncPkt.snapshot(ServerConfig.settings());
            checkEquals(snapshot.disabledMods().contains("policy_marker"),
                    ServerConfig.isModDisabled("policy_marker"), "Synchronization preceded admission publication");
            sent.add(snapshot);
        });
        try {
            ServerConfig.publish(before);
            checkEquals(0, sent.size(), "Unchanged policy synchronized");
            ServerConfig.Settings serverOnly = new ServerConfig.Settings(before.maxPileHeight() == 1 ? 2 : 1,
                    before.enableStorageStackBlock(), before.enableSinglesStackBlock(), before.enableBarStackBlock(),
                    before.disableMods(), List.of("minecraft:stone"), before.renderGalleryPlacementsPerTick(),
                    before.galleryEnabled(), before.galleryPermissionLevel(), before.genMods(), before.genItems());
            ServerConfig.publish(serverOnly);
            checkEquals(0, sent.size(), "Server-only policy synchronized");
            ServerConfig.Settings flags = new ServerConfig.Settings(serverOnly.maxPileHeight(),
                    !before.enableStorageStackBlock(), before.enableSinglesStackBlock(), before.enableBarStackBlock(),
                    before.disableMods(), serverOnly.disableItems(), before.renderGalleryPlacementsPerTick(),
                    before.galleryEnabled(), before.galleryPermissionLevel(), before.genMods(), before.genItems());
            ServerConfig.publish(flags);
            checkEquals(1, sent.size(), "Flag change did not synchronize once");
            ServerConfig.publish(flags);
            checkEquals(1, sent.size(), "Repeated publication synchronized");
            ServerConfig.publish(withNamespaces(flags, List.of("policy_marker"), List.of("gallery_marker")));
            checkEquals(2, sent.size(), "Namespace changes did not synchronize once");
            List<String> oversized = IntStream.rangeClosed(0, ServerConfig.MAX_NAMESPACE_ENTRIES)
                    .mapToObj(i -> "namespace_" + i).toList();
            check(!ServerConfig.publish(withNamespaces(flags, oversized, List.of())), "Oversize policy accepted");
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
    public static void admissionSeparatesPlayersAutomationAndTypes(GameTestHelper helper, ServerPlayer player) {
        ServerConfig.Settings before = ServerConfig.settings();
        try {
            ServerConfig.Settings policy = new ServerConfig.Settings(before.maxPileHeight(), true, true, true,
                    List.of(), List.of("minecraft:stone"), before.renderGalleryPlacementsPerTick(),
                    before.galleryEnabled(), before.galleryPermissionLevel(), before.genMods(), before.genItems());
            ServerConfig.publish(policy);
            ItemStack stone = new ItemStack(Items.STONE);
            check(STORAGE_STACK.admits(stone, null), "Exact-item denial reached automation");
            check(!STORAGE_STACK.admits(stone, player), "Player bypassed exact-item denial");
            check(!BlockType.permitsPlayerItemAndNotify(stone, player), "Player policy gate ignored denial");
            for (BlockType type : BlockType.values()) {
                check(!type.admits(ItemStack.EMPTY, null), "Empty automation input accepted");
                check(!type.admits(ItemStack.EMPTY, player), "Empty player input accepted");
            }
            ItemStack bar = new ItemStack(GameTestScaffold.firstBarItem());
            check(BAR_STACK.admits(bar, null), "Tagged bar refused");
            check(STORAGE_STACK.admits(bar, null), "Storage refused tagged bar");
            check(!SINGLES_STACK.admits(bar, null), "Singles accepted tagged bar");
            policy = new ServerConfig.Settings(policy.maxPileHeight(), true, true, true, List.of(),
                    List.of("minecraft:stone", BuiltInRegistries.ITEM.getKey(bar.getItem()).toString()),
                    policy.renderGalleryPlacementsPerTick(), policy.galleryEnabled(), policy.galleryPermissionLevel(),
                    policy.genMods(), policy.genItems());
            ServerConfig.publish(policy);
            check(!BAR_STACK.admits(bar, player), "Player bypassed tagged-item denial");
            check(BAR_STACK.admits(bar, null), "Tagged-item denial reached automation");
            check(!SINGLES_STACK.admits(bar, null), "Player-only denial changed type eligibility");
            ServerConfig.publish(withNamespaces(policy, List.of("minecraft"), policy.genMods()));
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
            check(ServerConfig.publish(withNamespaces(before,
                    List.of(" Minecraft ", "minecraft", "bad/name"),
                    List.of(" EXAMPLE ", "example", "bad:name", ""))), "Valid policy refused");
            checkEquals(List.of("minecraft"), ServerConfig.settings().disableMods(), "Deny normalization");
            checkEquals(List.of("example"), ServerConfig.settings().genMods(), "Gallery normalization");
            check(ServerConfig.isModDisabled("minecraft"), "Admission differs from published policy");

            List<String> full = IntStream.range(0, ServerConfig.MAX_NAMESPACE_ENTRIES)
                    .mapToObj(i -> "policy_" + i).toList();
            check(ServerConfig.publish(withNamespaces(before, full, full)), "Boundary policy refused");
            ServerConfig.Settings accepted = ServerConfig.settings();
            List<String> tooMany = IntStream.rangeClosed(0, ServerConfig.MAX_NAMESPACE_ENTRIES)
                    .mapToObj(i -> "policy_" + i).toList();
            check(!ServerConfig.publish(withNamespaces(before, tooMany, List.of())), "Oversize deny accepted");
            check(!ServerConfig.publish(withNamespaces(before, List.of(), tooMany)), "Oversize gallery accepted");
            check(!ServerConfig.publish(withNamespaces(before,
                    List.of("a".repeat(ServerConfig.MAX_NAMESPACE_LENGTH + 1)), List.of())),
                    "Overlong namespace accepted");
            check(ServerConfig.settings() == accepted, "Rejection replaced previous policy");
            check(ServerConfig.isModDisabled("policy_0"), "Rejected policy changed admission");

            for (String list : List.of("gen", "deny")) {
                int result = server.getCommands().getDispatcher().execute("ss " + list + " mod add extra",
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
     * the disabled types while /ss write list uses the new gallery list. Reconnect to a server
     * with different lists and enable flags to see the whole snapshot replaced. This fixture also
     * injects malformed wire data and checks the maximum legal payload, which ordinary UI cannot send.
     */
    public static void policyPacketsValidateBeforePublication(GameTestHelper helper) {
        ConfigSyncPkt previous = ClientServerSettings.snapshot();
        try {
            ConfigSyncPkt changed = new ConfigSyncPkt(false, true, false, List.of("gallery"), List.of("denied"));
            ClientServerSettings.apply(roundTrip(changed));
            check(!ClientServerSettings.isBlockTypeEnabled(STORAGE_STACK), "Storage flag not applied");
            check(ClientServerSettings.isBlockTypeEnabled(SINGLES_STACK), "Singles flag not applied");
            check(!ClientServerSettings.isBlockTypeEnabled(BAR_STACK), "Bar flag not applied");
            checkEquals(List.of("gallery"), ClientServerSettings.genMods(), "Gallery list not applied");
            check(ClientServerSettings.isDisabled("denied"), "Deny list not applied");

            List<String> maximum = IntStream.range(0, ServerConfig.MAX_NAMESPACE_ENTRIES)
                    .mapToObj(i -> "a".repeat(ServerConfig.MAX_NAMESPACE_LENGTH - 3)
                            + String.format(java.util.Locale.ROOT, "%03d", i)).toList();
            ConfigSyncPkt boundary = new ConfigSyncPkt(true, false, true, maximum, maximum);
            checkEquals(boundary, roundTrip(boundary), "Maximum packet did not round trip");
            checkEquals(new ConfigSyncPkt(true, true, true, List.of(), List.of()),
                    roundTrip(new ConfigSyncPkt(true, true, true, List.of(), List.of())), "Empty lists");

            reject(buf -> buf.writeVarInt(-1));
            reject(buf -> buf.writeVarInt(ServerConfig.MAX_NAMESPACE_ENTRIES + 1));
            reject(buf -> buf.writeVarInt(0).writeVarInt(ServerConfig.MAX_NAMESPACE_ENTRIES + 1));
            for (String invalid : List.of("", "Uppercase", "bad/name", "bad:name",
                    "a".repeat(ServerConfig.MAX_NAMESPACE_LENGTH + 1))) {
                reject(buf -> buf.writeVarInt(1).writeUtf(invalid).writeVarInt(0));
                reject(buf -> buf.writeVarInt(0).writeVarInt(1).writeUtf(invalid));
            }
            reject(buf -> buf.writeVarInt(2).writeUtf("duplicate").writeUtf("duplicate").writeVarInt(0));
            reject(buf -> buf.writeVarInt(0).writeVarInt(2).writeUtf("duplicate").writeUtf("duplicate"));
            reject(buf -> buf.writeVarInt(1));
            reject(buf -> buf.writeVarInt(0).writeVarInt(0).writeByte(0));
            reject(buf -> buf.writeZero(ConfigSyncPkt.MAX_PACKET_BYTES));
            checkEquals(List.of("gallery"), ClientServerSettings.genMods(), "Rejected data changed lists");

            ClientServerSettings.apply(new ConfigSyncPkt(true, true, true, List.of(), List.of()));
            check(ClientServerSettings.genMods().isEmpty(), "Empty snapshot retained gallery list");
            check(ClientServerSettings.disabledMods().isEmpty(), "Empty snapshot retained deny list");
        } finally {
            ClientServerSettings.apply(previous);
        }
        helper.succeed();
    }

    private static ConfigSyncPkt roundTrip(ConfigSyncPkt packet) {
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        try {
            ConfigSyncPkt.encode(packet, buf);
            check(buf.readableBytes() <= ConfigSyncPkt.MAX_PACKET_BYTES, "Packet exceeds calculated bound");
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
        return new ServerConfig.Settings(source.maxPileHeight(), source.enableStorageStackBlock(),
                source.enableSinglesStackBlock(), source.enableBarStackBlock(), disabled, source.disableItems(),
                source.renderGalleryPlacementsPerTick(), source.galleryEnabled(), source.galleryPermissionLevel(),
                gallery, source.genItems());
    }
}
