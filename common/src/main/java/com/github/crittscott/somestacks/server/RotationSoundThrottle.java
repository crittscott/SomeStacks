package com.github.crittscott.somestacks.server;

import net.minecraft.server.level.ServerPlayer;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Limits repeated rotation sounds without limiting the rotations themselves: rotation remains
 * available every tick, but a held gesture does not broadcast twenty sounds per second.
 */
public final class RotationSoundThrottle {
    /** Minimum ticks between rotation sounds initiated by the same player. */
    private static final long SOUND_INTERVAL_TICKS = 4L;

    private static final Map<UUID, Long> lastSoundTick = new HashMap<>();

    private RotationSoundThrottle() {}

    /** Claims this player's rotation-sound allowance; the rotation itself has already happened. */
    public static boolean claim(ServerPlayer player) {
        UUID id = player.getUUID();
        long tick = player.serverLevel().getGameTime();
        Long last = lastSoundTick.get(id);
        if (last != null && tick - last < SOUND_INTERVAL_TICKS) {
            return false;
        }
        lastSoundTick.put(id, tick);
        return true;
    }

    public static void clearAll() {
        lastSoundTick.clear();
    }

    public static void clear(UUID playerId) {
        lastSoundTick.remove(playerId);
    }
}
