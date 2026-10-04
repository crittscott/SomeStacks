package com.github.crittscott.somestacks.server;

import net.minecraft.server.level.ServerPlayer;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Limits repeated rotation sounds without limiting the rotations themselves. */
public final class RotationSoundThrottle {
    private static final long SOUND_INTERVAL_TICKS = 4L;
    private static final Map<UUID, Long> lastSoundTick = new HashMap<>();

    private RotationSoundThrottle() {}

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

    public static void clear(UUID playerId) {
        lastSoundTick.remove(playerId);
    }
}
