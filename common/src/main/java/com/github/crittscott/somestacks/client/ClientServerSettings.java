package com.github.crittscott.somestacks.client;

import com.github.crittscott.somestacks.network.ConfigSyncPkt;
import com.github.crittscott.somestacks.util.BlockType;

import net.minecraft.world.entity.player.Player;

import java.util.List;

import javax.annotation.Nullable;

/** Complete server policy snapshot, published and read on the client thread. */
public final class ClientServerSettings {
    private static ConfigSyncPkt snapshot =
            new ConfigSyncPkt(true, true, true, List.of(), List.of());

    private ClientServerSettings() {}

    /**
     * The last policy the server sent, or everything enabled with empty lists before the first
     * arrives.
     */
    public static ConfigSyncPkt snapshot() {
        return snapshot;
    }

    /**
     * Installs a received policy snapshot. A selected placement type the new policy disables is
     * advanced at once, with the mode message when a local player exists to show it; the login-time
     * sync can arrive before one does.
     */
    public static void apply(ConfigSyncPkt packet, @Nullable Player localPlayer) {
        snapshot = packet;
        if (ClientGestures.leaveDisabledMode() && localPlayer != null) {
            ClientGestures.displayModeMessage(localPlayer);
        }
    }

    /** Whether the server's policy lets players place this stack type. */
    public static boolean isBlockTypeEnabled(BlockType type) {
        return switch (type) {
            case STORAGE_STACK -> snapshot.enableStack();
            case SINGLES_STACK -> snapshot.enableSingles();
            case BAR_STACK -> snapshot.enableBar();
        };
    }

    public static List<String> genMods() {
        return snapshot.genMods();
    }

    /** Whether the server's policy disables items from this namespace. */
    public static boolean isDisabled(String namespace) {
        return snapshot.disabledMods().contains(namespace);
    }
}
