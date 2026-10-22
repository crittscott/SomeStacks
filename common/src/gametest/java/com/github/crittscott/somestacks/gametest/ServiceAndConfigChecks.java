package com.github.crittscott.somestacks.gametest;

import com.github.crittscott.somestacks.PlatformServices;
import com.github.crittscott.somestacks.ServerConfig;
import com.github.crittscott.somestacks.ServerLifecycle;
import com.github.crittscott.somestacks.SomeStacksCommon;
import com.github.crittscott.somestacks.server.StackSounds;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;
import java.util.Locale;
import java.util.UUID;

import static com.github.crittscott.somestacks.gametest.GameTestScaffold.check;
import static com.github.crittscott.somestacks.gametest.GameTestScaffold.checkEquals;

/** Shared lifecycle and policy checks using the installed loader services. */
public final class ServiceAndConfigChecks {
    private ServiceAndConfigChecks() {}

    /** To reproduce in-game: reconnect and rotate a stack; a previous session's rotation-sound pacing must not silence the new session. */
    public static void installedServicesAndLogoutResetPacing(GameTestHelper helper, ServerPlayer player) {
        check(PlatformServices.configFolder() != null, "Missing loader config path");
        check(!PlatformServices.modVersion(SomeStacksCommon.MODID).equals("unknown"),
                "Missing loader mod version");
        check(PlatformServices.editAuthority() != null, "Missing edit authority");
        ServerLifecycle.logout(player.getUUID());
        try {
            check(StackSounds.claimRotation(player), "Initial rotation sound refused");
            check(!StackSounds.claimRotation(player), "Repeated sound accepted");
            ServerLifecycle.logout(player.getUUID());
            check(StackSounds.claimRotation(player), "Logout retained sound pacing");
        } finally {
            ServerLifecycle.logout(player.getUUID());
        }
        helper.succeed();
    }

    /**
     * Synthetic raw policy inputs obey the same numeric ranges before either loader publishes them.
     * To check valid boundaries in-game, set pile height to 1 or 64, gallery permission to 0 or 4,
     * and gallery speed to 1 in the world config, then reload and use the affected features. Invalid
     * file values also pass through loader-specific parsing/correction; this fixture isolates the
     * shared policy constructor and does not change the running world's policy.
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
            ServerConfig.Settings policy = new ServerConfig.Settings(values[0],
                    defaults.enableStorageStackBlock(), defaults.enableSinglesStackBlock(),
                    defaults.enableBarStackBlock(), defaults.disableMods(), defaults.disableItems(),
                    values[1], defaults.galleryEnabled(), values[2], defaults.genMods(), defaults.genItems());
            checkEquals(values[3], policy.maxPileHeight(), "Pile height range");
            checkEquals(values[4], policy.renderGalleryPlacementsPerTick(), "Gallery speed range");
            checkEquals(values[5], policy.galleryPermissionLevel(), "Gallery permission range");
        }
        helper.succeed();
    }

    /** To reproduce in-game: add then remove a namespace through /ss deny mod; policy and admission change immediately while other settings remain unchanged. */
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
            check(ServerConfig.isModDisabled(marker.toUpperCase(Locale.ROOT)),
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
     * world's somestacks-server.toml (Forge) or somestacks-server.json (Fabric), then run
     * /ss reload. The reply counts online players, and /ss gen mod list shows the
     * new entry. The fixture writes the stored policy through the loader's saver without
     * publishing it, and restores both the file and the published policy afterward.
     */
    public static void reloadCommandPublishesStoredPolicy(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        ServerConfig.Settings before = ServerConfig.settings();
        String marker = "somestacks_gametest_reload_" + UUID.randomUUID().toString().replace("-", "");
        ServerConfig.Settings stored = new ServerConfig.Settings(before.maxPileHeight(),
                before.enableStorageStackBlock(), before.enableSinglesStackBlock(), before.enableBarStackBlock(),
                before.disableMods(), before.disableItems(), before.renderGalleryPlacementsPerTick(),
                before.galleryEnabled(), before.galleryPermissionLevel(), List.of(marker), before.genItems());
        try {
            ServerConfig.publish(stored);
            ServerConfig.save();
            ServerConfig.publish(before);
            check(!ServerConfig.ListSetting.GEN_MODS.get().contains(marker), "Fixture published the stored policy");

            int synced = server.getCommands().getDispatcher().execute("ss reload",
                    server.createCommandSourceStack().withSuppressedOutput());

            checkEquals(List.of(marker), ServerConfig.ListSetting.GEN_MODS.get(), "Reload did not publish stored policy");
            checkEquals(server.getPlayerList().getPlayerCount(), synced, "Reload did not count online players");
        } catch (CommandSyntaxException exception) {
            throw new AssertionError("Reload command failed", exception);
        } finally {
            ServerConfig.publish(before);
            ServerConfig.save();
        }
        helper.succeed();
    }
}
