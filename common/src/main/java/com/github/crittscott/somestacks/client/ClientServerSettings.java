package com.github.crittscott.somestacks.client;

import com.github.crittscott.somestacks.network.ConfigSyncPkt;
import com.github.crittscott.somestacks.util.BlockType;

import java.util.List;

/** Complete server policy snapshot, published and read on the client thread. */
public final class ClientServerSettings {
    private static ConfigSyncPkt snapshot = new ConfigSyncPkt(true, true, true, List.of(), List.of());

    private ClientServerSettings() {}

    public static ConfigSyncPkt snapshot() {
        return snapshot;
    }

    public static void apply(ConfigSyncPkt packet) {
        snapshot = packet;
    }

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

    public static List<String> disabledMods() {
        return snapshot.disabledMods();
    }

    public static boolean isDisabled(String namespace) {
        return snapshot.disabledMods().contains(namespace);
    }
}
