package com.github.crittscott.somestacks.client;

import com.github.crittscott.somestacks.block.StackBlockEntity;
import com.github.crittscott.somestacks.network.ConfigSyncPkt;
import com.github.crittscott.somestacks.util.BlockType;
import java.util.List;
import java.util.Set;

/** Connection-owned server policy, published as one immutable value on the client thread. */
public final class ClientServerSettings {
    private record Policy(ConfigSyncPkt snapshot, Set<String> denied) {}
    private static Policy policy = defaults();

    private ClientServerSettings() {}

    private static Policy defaults() {
        return new Policy(new ConfigSyncPkt(true, true, true, List.of(), List.of()), Set.of());
    }

    public static void apply(ConfigSyncPkt snapshot) {
        policy = new Policy(snapshot, Set.copyOf(snapshot.disabledMods()));
    }

    public static boolean isBlockTypeEnabled(BlockType type) {
        ConfigSyncPkt snapshot = policy.snapshot();
        return switch (type) {
            case STORAGE_STACK -> snapshot.enableStorage();
            case SINGLES_STACK -> snapshot.enableSingles();
            case BAR_STACK -> snapshot.enableBar();
        };
    }

    /** Changes a flag through the same complete publication used by received snapshots. */
    public static void setBlockEnabled(BlockType type, boolean enabled) {
        ConfigSyncPkt previous = policy.snapshot();
        apply(new ConfigSyncPkt(type == BlockType.STORAGE_STACK ? enabled : previous.enableStorage(),
                type == BlockType.SINGLES_STACK ? enabled : previous.enableSingles(),
                type == BlockType.BAR_STACK ? enabled : previous.enableBar(),
                previous.genMods(), previous.disabledMods()));
    }

    public static List<String> genMods() { return policy.snapshot().genMods(); }
    public static Set<String> disabledMods() { return policy.denied(); }
    public static boolean isDisabled(String namespace) { return policy.denied().contains(namespace); }

    /** Replaces authoring lists without changing the enabled stack types. */
    public static void replace(List<String> genMods, List<String> disabledMods) {
        ConfigSyncPkt previous = policy.snapshot();
        apply(new ConfigSyncPkt(previous.enableStorage(), previous.enableSingles(), previous.enableBar(),
                genMods, disabledMods));
    }

    public static void clear() {
        policy = defaults();
        StackBlockEntity.resetClientUpdateDiagnostic();
    }
}
