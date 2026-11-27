package com.github.crittscott.somestacks.gametest;

import static com.github.crittscott.somestacks.gametest.GameTestScaffold.check;
import static com.github.crittscott.somestacks.gametest.GameTestScaffold.checkEquals;
import static com.github.crittscott.somestacks.gametest.GameTestScaffold.policy;

import com.github.crittscott.somestacks.ServerConfig;
import com.github.crittscott.somestacks.SomeStacksServer;
import com.github.crittscott.somestacks.network.ConfigSyncPkt;
import com.mojang.brigadier.exceptions.CommandSyntaxException;

import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.MinecraftServer;

import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

/** Shared command help and atomic namespace-policy validation. */
public final class PolicyParityChecks {
    private PolicyParityChecks() {}

    /**
     * To reproduce in-game: run /ss help for each listed command without operator permission;
     * usages and audience remain visible. As an operator add a mixed-case mod namespace, try an
     * invalid namespace and a 257-character name, and fill the list to 256 unique entries. Rejected
     * additions leave it intact. Reload an over-limit policy: the current policy remains active.
     */
    public static void commandHelpAndNamespaceLimits(GameTestHelper helper) {
        ServerConfig.Settings original = ServerConfig.settings();
        try {
            apply(withMods(original, List.of(" MineCraft ", "minecraft", "ExampleMod")));
            checkEquals(
                    List.of("minecraft", "examplemod"),
                    ServerConfig.settings().genMods(),
                    "Namespaces were not normalized and deduplicated in order");
            refuses(() -> ServerConfig.addListEntry(ServerConfig.GEN_MODS, "bad namespace"));
            refuses(() -> ServerConfig.addListEntry(ServerConfig.GEN_MODS, "x".repeat(257)));
            List<String> full =
                    IntStream.range(0, ServerConfig.MAX_NAMESPACE_ENTRIES)
                            .mapToObj(i -> "example_" + i)
                            .toList();
            apply(withMods(original, full));
            ServerConfig.Settings accepted = ServerConfig.settings();
            refuses(() -> ServerConfig.addListEntry(ServerConfig.GEN_MODS, "one_more"));
            checkEquals(
                    accepted, ServerConfig.settings(), "Rejected addition changed active policy");
            List<String> excessive =
                    IntStream.range(0, ServerConfig.MAX_NAMESPACE_ENTRIES + 1)
                            .mapToObj(i -> "example_" + i)
                            .toList();
            refuses(() -> apply(withMods(original, excessive)));
            checkEquals(
                    accepted,
                    ServerConfig.settings(),
                    "Rejected publication changed active policy");
            ServerConfig.Backend backend = ServerConfig.backend();
            try {
                ServerConfig.install(
                        new ServerConfig.Backend() {
                            public ServerConfig.Settings loadFor(MinecraftServer server) {
                                return withMods(original, excessive);
                            }

                            public ServerConfig.Settings reload() {
                                return withMods(original, excessive);
                            }

                            public boolean save(ServerConfig.Settings policy) {
                                return true;
                            }
                        });
                check(!ServerConfig.reload(), "Over-limit reload reported success");
                checkEquals(
                        accepted, ServerConfig.settings(), "Rejected reload changed active policy");
            } finally {
                ServerConfig.install(backend);
            }
            var server = helper.getLevel().getServer();
            check(
                    SomeStacksServer.reloadPolicy(server) >= 0,
                    "Valid policy reload was rejected");
            var packets = ConfigSyncPkt.currentPackets();
            checkEquals(
                    0,
                    SomeStacksServer.reloadPolicy(server),
                    "Unchanged reload sent client settings");
            checkEquals(
                    packets,
                    ConfigSyncPkt.currentPackets(),
                    "Unchanged reload rebuilt the packet generation");
            var source = server.createCommandSourceStack().withPermission(0).withSuppressedOutput();
            for (String topic :
                    List.of(
                            "item",
                            "write",
                            "gallery",
                            "ingotgallery",
                            "reload",
                            "gen",
                            "deny",
                            "help")) {
                checkEquals(
                        1,
                        server.getCommands().getDispatcher().execute("ss help " + topic, source),
                        "Help failed for " + topic);
            }
        } catch (CommandSyntaxException failure) {
            throw new GameTestAssertException(
                    "Registered command help failed: " + failure.getMessage());
        } finally {
            apply(original);
        }
        helper.succeed();
    }

    private static void apply(ServerConfig.Settings policy) {
        ServerConfig.apply(policy);
    }

    private static ServerConfig.Settings withMods(ServerConfig.Settings base, List<String> mods) {
        return policy(base, Map.of(ServerConfig.Setting.GEN_MODS, mods));
    }

    private static void refuses(Runnable action) {
        boolean refused = false;
        try {
            action.run();
        } catch (IllegalArgumentException expected) {
            refused = true;
        }
        check(refused, "Invalid namespace policy was accepted");
    }
}
