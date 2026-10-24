package com.github.crittscott.somestacks.gametest;

import com.github.crittscott.somestacks.ServerConfig;
import net.minecraft.gametest.framework.GameTestHelper;

import java.util.List;
import java.util.stream.IntStream;

import static com.github.crittscott.somestacks.gametest.GameTestScaffold.check;
import static com.github.crittscott.somestacks.gametest.GameTestScaffold.checkEquals;

/** Checks shared command help and atomic namespace-policy validation through the registered GameTests. */
public final class PolicyParityChecks {
    private PolicyParityChecks() {}

    /**
     * To reproduce in-game: run /ss help for each listed command without operator permission;
     * usages and audience remain visible. As an operator add a mixed-case mod namespace, try
     * an invalid namespace and a 257-character name, and fill the list to 256 unique entries.
     * Rejected additions leave it intact. Reload an over-limit policy: the current policy remains active.
     */
    public static void verify(GameTestHelper helper) {
        ServerConfig.Settings original = ServerConfig.settings();
        try {
            apply(withMods(original, List.of(" MineCraft ", "minecraft", "ExampleMod")));
            checkEquals(List.of("minecraft", "examplemod"), ServerConfig.settings().genMods(),
                    "Namespaces were not normalized and deduplicated in order");
            refuses(() -> ServerConfig.addListEntry(ServerConfig.ListSetting.GEN_MODS, "bad namespace"));
            refuses(() -> ServerConfig.addListEntry(ServerConfig.ListSetting.GEN_MODS, "x".repeat(257)));
            List<String> full = IntStream.range(0, ServerConfig.MAX_NAMESPACE_ENTRIES)
                    .mapToObj(i -> "example_" + i).toList();
            apply(withMods(original, full));
            ServerConfig.Settings accepted = ServerConfig.settings();
            refuses(() -> ServerConfig.addListEntry(ServerConfig.ListSetting.GEN_MODS, "one_more"));
            checkEquals(accepted, ServerConfig.settings(), "Rejected addition changed active policy");
            List<String> excessive = IntStream.range(0, ServerConfig.MAX_NAMESPACE_ENTRIES + 1)
                    .mapToObj(i -> "example_" + i).toList();
            refuses(() -> apply(withMods(original, excessive)));
            checkEquals(accepted, ServerConfig.settings(), "Rejected publication changed active policy");

            var server = helper.getLevel().getServer();
            var source = server.createCommandSourceStack().withPermission(0).withSuppressedOutput();
            for (String topic : List.of("item", "write", "gallery", "ingotgallery", "reload", "gen", "deny", "help")) {
                checkEquals(1, server.getCommands().getDispatcher().execute("ss help " + topic, source),
                        "Help failed for " + topic);
            }
        } catch (com.mojang.brigadier.exceptions.CommandSyntaxException failure) {
            throw new AssertionError("Registered command help failed", failure);
        } finally {
            apply(original);
        }
    }

    private static void apply(ServerConfig.Settings policy) {
        if (!ServerConfig.publish(policy)) throw new IllegalArgumentException("Rejected policy");
    }

    private static ServerConfig.Settings withMods(ServerConfig.Settings policy, List<String> mods) {
        return new ServerConfig.Settings(policy.maxPileHeight(), policy.enableStorageStackBlock(),
                policy.enableSinglesStackBlock(), policy.enableBarStackBlock(), policy.disableMods(),
                policy.disableItems(), policy.renderGalleryPlacementsPerTick(), policy.galleryEnabled(),
                policy.galleryPermissionLevel(), mods, policy.genItems());
    }

    private static void refuses(Runnable action) {
        boolean refused = false;
        try { action.run(); } catch (IllegalArgumentException expected) { refused = true; }
        check(refused, "Invalid namespace policy was accepted");
    }
}
