package com.github.crittscott.somestacks.client;

import com.github.crittscott.somestacks.network.ConfigSyncPkt;
import com.github.crittscott.somestacks.renderconfig.ItemRenderConfig;
import com.github.crittscott.somestacks.util.BlockType;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Stages config chunks and atomically publishes a complete server snapshot. */
public final class ClientRenderPacketSink {
    private static int generation = -1;
    private static boolean enableStack;
    private static boolean enableSingles;
    private static boolean enableBar;
    private static int expectedOverrides;
    private static int expectedGenMods;
    private static int expectedDisabledMods;
    private static final Map<ResourceLocation, ItemRenderConfig> OVERRIDES =
            new LinkedHashMap<>();
    private static final List<String> GEN_MODS = new ArrayList<>();
    private static final List<String> DISABLED_MODS = new ArrayList<>();

    private ClientRenderPacketSink() {}

    public static void apply(ConfigSyncPkt packet) {
        if (packet.first()) {
            begin(packet);
        } else if (packet.generation() != generation) {
            throw new IllegalStateException(
                    "Config sync chunk without matching first chunk: " + packet.generation());
        }

        verifyMetadata(packet);
        for (Map.Entry<ResourceLocation, ItemRenderConfig> entry
                : packet.renderOverrides().entrySet()) {
            if (OVERRIDES.put(entry.getKey(), entry.getValue()) != null) {
                throw new IllegalStateException(
                        "Duplicate synchronized render override: " + entry.getKey());
            }
        }
        addUnique(GEN_MODS, packet.genMods(), "gallery mod");
        addUnique(DISABLED_MODS, packet.disabledMods(), "disabled mod");
        verifyNotOverfull();

        if (packet.last()) {
            finish();
        }
    }

    private static void begin(ConfigSyncPkt packet) {
        generation = packet.generation();
        enableStack = packet.enableStack();
        enableSingles = packet.enableSingles();
        enableBar = packet.enableBar();
        expectedOverrides = packet.totalOverrides();
        expectedGenMods = packet.totalGenMods();
        expectedDisabledMods = packet.totalDisabledMods();
        OVERRIDES.clear();
        GEN_MODS.clear();
        DISABLED_MODS.clear();
    }

    private static void verifyMetadata(ConfigSyncPkt packet) {
        if (packet.enableStack() != enableStack
                || packet.enableSingles() != enableSingles
                || packet.enableBar() != enableBar
                || packet.totalOverrides() != expectedOverrides
                || packet.totalGenMods() != expectedGenMods
                || packet.totalDisabledMods() != expectedDisabledMods) {
            throw new IllegalStateException(
                    "Config sync metadata changed within generation " + generation);
        }
    }

    private static void verifyNotOverfull() {
        if (OVERRIDES.size() > expectedOverrides
                || GEN_MODS.size() > expectedGenMods
                || DISABLED_MODS.size() > expectedDisabledMods) {
            throw new IllegalStateException("Config sync exceeded its declared totals");
        }
    }

    private static void addUnique(List<String> destination, List<String> additions, String label) {
        for (String addition : additions) {
            if (destination.contains(addition)) {
                throw new IllegalStateException(
                        "Duplicate synchronized " + label + " namespace: " + addition);
            }
            destination.add(addition);
        }
    }

    private static void finish() {
        if (OVERRIDES.size() != expectedOverrides
                || GEN_MODS.size() != expectedGenMods
                || DISABLED_MODS.size() != expectedDisabledMods) {
            throw new IllegalStateException("Config sync ended before reaching its declared totals");
        }

        StackState.setBlockEnabled(BlockType.STORAGE_STACK, enableStack);
        StackState.setBlockEnabled(BlockType.SINGLES_STACK, enableSingles);
        StackState.setBlockEnabled(BlockType.BAR_STACK, enableBar);
        ItemRenderOverrides.setSyncedServerOverrides(OVERRIDES);
        ClientRenderToolState.replace(GEN_MODS, DISABLED_MODS);
        generation = -1;
    }
}
