package com.github.crittscott.somestacks.server;

import com.github.crittscott.somestacks.util.StackMode;
import net.minecraft.server.level.ServerPlayer;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Server-side placement mode and modifier state last reported by each connected client. */
public final class ServerGestureState {
    private static final State DEFAULT = new State(StackMode.STORAGE_STACK, false, null);
    private static final Map<UUID, State> states = new HashMap<>();

    private ServerGestureState() {}

    public static State get(ServerPlayer player) {
        return states.getOrDefault(player.getUUID(), DEFAULT);
    }

    public static void set(ServerPlayer player, StackMode mode, boolean modifierDown) {
        states.put(player.getUUID(), new State(mode, modifierDown, get(player).lastSoundTick()));
    }

    public static void clear(UUID playerId) {
        states.remove(playerId);
    }

    private static final long SOUND_INTERVAL_TICKS = 4L;

    /** Claims one rotation sound without limiting the rotations themselves. */
    public static boolean claimSound(ServerPlayer player) {
        State state = get(player);
        long now = player.level().getGameTime();
        if (state.lastSoundTick() != null && now - state.lastSoundTick() < SOUND_INTERVAL_TICKS) return false;
        states.put(player.getUUID(), new State(state.mode(), state.modifierDown(), now));
        return true;
    }

    public static void resetSound(UUID id) {
        State state = states.get(id);
        if (state != null) states.put(id, new State(state.mode(), state.modifierDown(), null));
    }

    public record State(StackMode mode, boolean modifierDown, @javax.annotation.Nullable Long lastSoundTick) {}
}
