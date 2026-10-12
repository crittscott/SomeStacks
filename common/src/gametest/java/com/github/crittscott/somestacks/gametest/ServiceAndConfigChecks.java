package com.github.crittscott.somestacks.gametest;

import com.github.crittscott.somestacks.PlatformServices;
import com.github.crittscott.somestacks.ServerConfig;
import com.github.crittscott.somestacks.ServerLifecycle;
import com.github.crittscott.somestacks.SomeStacksCommon;
import com.github.crittscott.somestacks.server.GestureThrottle;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;

import static com.github.crittscott.somestacks.gametest.GameTestScaffold.check;
import static com.github.crittscott.somestacks.gametest.GameTestScaffold.checkEquals;

/** Shared lifecycle and policy checks using the installed loader services. */
public final class ServiceAndConfigChecks {
    private ServiceAndConfigChecks() {}

    /** To reproduce in-game: reconnect and rotate a stack; a previous session's gesture/sound pacing must not block the new session. */
    public static void installedServicesAndLogoutResetPacing(GameTestHelper helper, ServerPlayer player) {
        check(PlatformServices.configFolder() != null, "Missing loader config path");
        check(!PlatformServices.modVersion(SomeStacksCommon.MODID).equals("unknown"),
                "Missing loader mod version");
        check(PlatformServices.editAuthority() != null, "Missing edit authority");
        ServerLifecycle.logout(player.getUUID());
        try {
            check(GestureThrottle.claimTick(player), "Initial gesture refused");
            check(!GestureThrottle.claimTick(player), "Repeated gesture accepted");
            check(GestureThrottle.claimRotationSound(player), "Initial rotation sound refused");
            check(!GestureThrottle.claimRotationSound(player), "Repeated sound accepted");
            ServerLifecycle.logout(player.getUUID());
            check(GestureThrottle.claimTick(player), "Logout retained gesture pacing");
            check(GestureThrottle.claimRotationSound(player), "Logout retained sound pacing");
        } finally {
            ServerLifecycle.logout(player.getUUID());
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
            check(ServerConfig.isModDisabled(marker.toUpperCase(java.util.Locale.ROOT)),
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
}
