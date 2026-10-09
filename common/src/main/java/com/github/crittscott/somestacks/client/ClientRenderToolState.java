package com.github.crittscott.somestacks.client;

import java.util.List;
import java.util.Set;

/** Passive server lists used only when the local player invokes a render-authoring command. */
public final class ClientRenderToolState {
    private static List<String> genMods = List.of();
    private static Set<String> disabledMods = Set.of();

    private ClientRenderToolState() {}

    public static void replace(List<String> genMods, List<String> disabledMods) {
        ClientRenderToolState.genMods = List.copyOf(genMods);
        ClientRenderToolState.disabledMods = Set.copyOf(disabledMods);
    }

    public static List<String> genMods() {
        return genMods;
    }

    /** Immutable synchronized namespace deny set used by local authoring commands. */
    public static Set<String> disabledMods() {
        return disabledMods;
    }

    public static boolean isDisabled(String namespace) {
        return disabledMods.contains(namespace);
    }
}
