package com.github.crittscott.somestacks.client;

import com.github.crittscott.somestacks.network.ConfigSyncPkt;
import com.github.crittscott.somestacks.util.BlockType;

import net.minecraft.world.entity.player.Player;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import javax.annotation.Nullable;

/** Stages config chunks and atomically publishes a complete server snapshot. */
public final class ClientRenderPacketSink {
    private static int generation = -1;
    private static int chunks;
    private static int bytes;
    private static boolean enableStack;
    private static boolean enableSingles;
    private static boolean enableBar;
    private static int expectedGenMods;
    private static int expectedDisabledMods;
    private static final Set<String> GEN_MODS = new LinkedHashSet<>();
    private static final Set<String> DISABLED_MODS = new LinkedHashSet<>();

    private ClientRenderPacketSink() {}

    /**
     * Stages a decoded chunk on the client thread. A first chunk replaces incomplete staging; only
     * a complete final chunk publishes flags and lists. Publication moves the selected placement
     * mode off a type the new policy disables, with the mode message when a local player exists to
     * show it. Rejection clears staging and throws a runtime exception to the loader's receive
     * handler.
     */
    public static void apply(ConfigSyncPkt packet, @Nullable Player localPlayer) {
        try {
            stage(packet, localPlayer);
        } catch (RuntimeException rejected) {
            clear();
            throw rejected;
        }
    }

    /** Discards incomplete data at disconnect or after rejection. */
    public static void clear() {
        generation = -1;
        chunks = 0;
        bytes = 0;
        GEN_MODS.clear();
        DISABLED_MODS.clear();
    }

    private static void stage(ConfigSyncPkt packet, @Nullable Player localPlayer) {
        if (packet.first()) {
            begin(packet);
        } else if (packet.generation() != generation) {
            throw new IllegalStateException(
                    "Config sync chunk without matching first chunk: " + packet.generation());
        }

        verifyMetadata(packet);
        chunks++;
        bytes += packet.budgetBytes();
        if (chunks > ConfigSyncPkt.MAX_GENERATION_CHUNKS
                || bytes > ConfigSyncPkt.MAX_GENERATION_BYTES
                || (!packet.last() && packet.genMods().isEmpty() && packet.disabledMods().isEmpty())
                || GEN_MODS.size() + packet.genMods().size() > expectedGenMods
                || DISABLED_MODS.size() + packet.disabledMods().size() > expectedDisabledMods) {
            throw new IllegalStateException("Config sync exceeded its receive budget");
        }
        addUnique(GEN_MODS, packet.genMods(), "gallery mod");
        addUnique(DISABLED_MODS, packet.disabledMods(), "disabled mod");

        if (packet.last()) {
            finish(localPlayer);
        }
    }

    private static void begin(ConfigSyncPkt packet) {
        clear();
        generation = packet.generation();
        enableStack = packet.enableStack();
        enableSingles = packet.enableSingles();
        enableBar = packet.enableBar();
        expectedGenMods = packet.totalGenMods();
        expectedDisabledMods = packet.totalDisabledMods();
    }

    private static void verifyMetadata(ConfigSyncPkt packet) {
        if (packet.enableStack() != enableStack
                || packet.enableSingles() != enableSingles
                || packet.enableBar() != enableBar
                || packet.totalGenMods() != expectedGenMods
                || packet.totalDisabledMods() != expectedDisabledMods) {
            throw new IllegalStateException(
                    "Config sync metadata changed within generation " + generation);
        }
    }

    private static void addUnique(Set<String> destination, List<String> additions, String label) {
        for (String addition : additions) {
            if (!destination.add(addition)) {
                throw new IllegalStateException(
                        "Duplicate synchronized " + label + " namespace: " + addition);
            }
        }
    }

    private static void finish(@Nullable Player localPlayer) {
        if (GEN_MODS.size() != expectedGenMods || DISABLED_MODS.size() != expectedDisabledMods) {
            throw new IllegalStateException(
                    "Config sync ended before reaching its declared totals");
        }

        StackState.setBlockEnabled(BlockType.STORAGE_STACK, enableStack);
        StackState.setBlockEnabled(BlockType.SINGLES_STACK, enableSingles);
        StackState.setBlockEnabled(BlockType.BAR_STACK, enableBar);
        ClientRenderToolState.replace(List.copyOf(GEN_MODS), List.copyOf(DISABLED_MODS));
        clear();
        if (ClientGestures.leaveDisabledMode() && localPlayer != null) {
            ClientGestures.displayModeMessage(localPlayer);
        }
    }
}
