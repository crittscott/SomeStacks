package com.github.crittscott.somestacks.client;

import com.github.crittscott.somestacks.block.StackBlockEntity;
import com.github.crittscott.somestacks.network.ConfigSyncPkt;
import com.github.crittscott.somestacks.util.BlockType;

import java.util.List;

/** Publishes one validated policy snapshot on the client thread. */
public final class ConfigSyncReceiver {
    private ConfigSyncReceiver() {}

    public static void apply(ConfigSyncPkt snapshot) {
        StackState.setBlockEnabled(BlockType.STORAGE_STACK, snapshot.enableStorage());
        StackState.setBlockEnabled(BlockType.SINGLES_STACK, snapshot.enableSingles());
        StackState.setBlockEnabled(BlockType.BAR_STACK, snapshot.enableBar());
        ClientRenderToolState.replace(snapshot.genMods(), snapshot.disabledMods());
    }

    /** Resets connection-owned presentation and its once-per-connection diagnostic. */
    public static void clear() {
        apply(new ConfigSyncPkt(true, true, true, List.of(), List.of()));
        StackBlockEntity.resetClientUpdateDiagnostic();
    }
}
