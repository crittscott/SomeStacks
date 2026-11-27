package com.github.crittscott.somestacks.server;

import com.github.crittscott.somestacks.util.StackMode;

import net.minecraft.server.level.ServerPlayer;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Server-side placement mode and modifier state last reported by each connected client. */
public final class ServerGestureState {
    private static final State DEFAULT = new State(StackMode.STORAGE_STACK, false);
    private static final Map<UUID, State> states = new HashMap<>();

    private ServerGestureState() {}

    /**
     * The player's last reported state, or Storage mode with the modifier up when this session has
     * no report from them; reading never records a state.
     */
    public static State get(ServerPlayer player) {
        return states.getOrDefault(player.getUUID(), DEFAULT);
    }

    /** Records a decoded report from the player's client; called on the server thread. */
    public static void set(ServerPlayer player, StackMode mode, boolean modifierDown) {
        states.put(player.getUUID(), new State(mode, modifierDown));
    }

    public static void clear(UUID playerId) {
        states.remove(playerId);
    }

    public static void clearAll() {
        states.clear();
    }

    public record State(StackMode mode, boolean modifierDown) {}
}
