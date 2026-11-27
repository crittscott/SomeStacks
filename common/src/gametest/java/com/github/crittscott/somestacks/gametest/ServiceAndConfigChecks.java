package com.github.crittscott.somestacks.gametest;

import static com.github.crittscott.somestacks.gametest.GameTestScaffold.check;
import static com.github.crittscott.somestacks.gametest.GameTestScaffold.checkEquals;
import static com.github.crittscott.somestacks.gametest.GameTestScaffold.policy;

import com.github.crittscott.somestacks.PlatformServices;
import com.github.crittscott.somestacks.ServerConfig;
import com.github.crittscott.somestacks.ServerLifecycle;
import com.github.crittscott.somestacks.SomeStacksCommon;
import com.github.crittscott.somestacks.server.StackInteractions;
import com.mojang.brigadier.exceptions.CommandSyntaxException;

import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/** Shared lifecycle and policy checks using the installed loader services. */
public final class ServiceAndConfigChecks {
    private ServiceAndConfigChecks() {}

    /**
     * To reproduce in-game: without operator permission, run /ss help followed by each of item,
     * write, gallery, ingotgallery, reload, gen, deny, and help. Every topic prints its usage and
     * audience, including the operator-only topics.
     */
    public static void commandHelpAnswersEveryTopic(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        var source = server.createCommandSourceStack().withPermission(0).withSuppressedOutput();
        try {
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
            throw new AssertionError("Registered command help failed", failure);
        }
        helper.succeed();
    }

    /**
     * No in-game form: rotation sounds are paced only a few ticks apart, and any reconnect takes
     * far longer, so a player cannot see whether logout cleared the pacing. It guards that a
     * repeated rotation sound within the pacing window is refused and that logout clears the
     * player's pacing. It also asserts that the loader installed {@code PlatformServices}: a config
     * folder, the mod's version (not "unknown"), and an edit authority.
     */
    public static void installedServicesAndLogoutResetPacing(
            GameTestHelper helper, ServerPlayer player) {
        check(PlatformServices.configFolder() != null, "Missing loader config path");
        check(
                !PlatformServices.modVersion(SomeStacksCommon.MODID).equals("unknown"),
                "Missing loader mod version");
        check(PlatformServices.editAuthority() != null, "Missing edit authority");
        ServerLifecycle.logout(player.getUUID());
        try {
            check(StackInteractions.claimRotationSound(player), "Initial rotation sound refused");
            check(!StackInteractions.claimRotationSound(player), "Repeated sound accepted");
            ServerLifecycle.logout(player.getUUID());
            check(StackInteractions.claimRotationSound(player), "Logout retained sound pacing");
        } finally {
            ServerLifecycle.logout(player.getUUID());
        }
        helper.succeed();
    }

    /**
     * Synthetic raw policy inputs obey the same numeric ranges before either loader publishes them.
     * To check valid boundaries in-game, set pile height to 1 or 64, gallery permission to 0 or 4,
     * and gallery speed to 1 in the world config, then reload and use the affected features.
     * Invalid file values also pass through loader-specific parsing/correction; this fixture
     * isolates the shared policy constructor and does not change the running world's policy.
     */
    public static void numericPolicyRangesAreShared(GameTestHelper helper) {
        ServerConfig.Settings defaults = ServerConfig.Settings.defaults();
        // Input height/speed/permission, followed by their expected normalized values.
        int[][] cases = {
            {Integer.MIN_VALUE, Integer.MIN_VALUE, Integer.MIN_VALUE, 1, 1, 0},
            {Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE, 64, Integer.MAX_VALUE, 4},
            {5, 17, 2, 5, 17, 2}
        };
        for (int[] values : cases) {
            ServerConfig.Settings normalized =
                    policy(
                            defaults,
                            Map.of(
                                    ServerConfig.Setting.MAX_PILE_HEIGHT, values[0],
                                    ServerConfig.Setting.PLACEMENTS_PER_TICK, values[1],
                                    ServerConfig.Setting.REQUIRED_PERMISSION_LEVEL, values[2]));
            checkEquals(values[3], normalized.maxPileHeight(), "Pile height range");
            checkEquals(
                    values[4], normalized.renderGalleryPlacementsPerTick(), "Gallery speed range");
            checkEquals(values[5], normalized.galleryPermissionLevel(), "Gallery permission range");
        }
        helper.succeed();
    }

    /**
     * To reproduce in-game: add then remove a namespace through /ss deny mod; policy and admission
     * change immediately while other settings remain unchanged.
     */
    public static void listEditsPublishImmutablePolicy(GameTestHelper helper) {
        ServerConfig.Settings before = ServerConfig.settings();
        String marker = "somestacks_gametest_policy";
        check(!ServerConfig.isModDisabled(marker), "Test marker already disabled");
        boolean added = ServerConfig.addListEntry(ServerConfig.ListSetting.DISABLE_MODS, marker);
        try {
            check(added, "Policy list edit failed");
            ServerConfig.Settings after = ServerConfig.settings();
            check(after != before, "Policy list edit reused mutable settings");
            check(!before.disableMods().contains(marker), "Previous policy snapshot changed");
            check(after.disableMods().contains(marker), "New policy omitted edit");
            check(
                    ServerConfig.isModDisabled(marker.toUpperCase(Locale.ROOT)),
                    "Admission cache did not follow edit");
            try {
                ServerConfig.ListSetting.DISABLE_MODS.get().add("unexpected");
                check(false, "Policy list is mutable");
            } catch (UnsupportedOperationException expected) {
                // Readers cannot edit published policy.
            }
        } finally {
            if (added) {
                ServerConfig.removeListEntry(ServerConfig.ListSetting.DISABLE_MODS, marker);
            }
        }
        checkEquals(before, ServerConfig.settings(), "List edit/remove changed other policy");
        helper.succeed();
    }

    /**
     * To reproduce in-game: while the server runs, add a mod id to render_gallery.gen_mods in the
     * active somestacks-server.toml, then run /ss reload. The reply counts online players, and /ss
     * gen mod list shows the new entry. The fixture writes the stored policy through the loader's
     * saver without publishing it, and restores both the file and the published policy afterward.
     */
    public static void reloadCommandPublishesStoredPolicy(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        ServerConfig.Settings before = ServerConfig.settings();
        String marker =
                "somestacks_gametest_reload_" + UUID.randomUUID().toString().replace("-", "");
        ServerConfig.Settings stored =
                policy(before, Map.of(ServerConfig.Setting.GEN_MODS, List.of(marker)));
        try {
            ServerConfig.publish(stored);
            ServerConfig.save();
            ServerConfig.publish(before);
            check(
                    !ServerConfig.ListSetting.GEN_MODS.get().contains(marker),
                    "Fixture published the stored policy");

            int result =
                    server.getCommands()
                            .getDispatcher()
                            .execute(
                                    "ss reload",
                                    server.createCommandSourceStack().withSuppressedOutput());

            checkEquals(
                    List.of(marker),
                    ServerConfig.ListSetting.GEN_MODS.get(),
                    "Reload did not publish stored policy");
            checkEquals(1, result, "Successful reload did not return command success");
        } catch (CommandSyntaxException exception) {
            throw new AssertionError("Reload command failed", exception);
        } finally {
            ServerConfig.publish(before);
            ServerConfig.save();
        }
        helper.succeed();
    }
}
