package com.github.crittscott.somestacks.server;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Stack action sound levels and throttled rotation playback. */
public final class StackSounds {
    private StackSounds() {}

    /** Minimum ticks between rotation sounds initiated by the same player. */
    private static final long SOUND_INTERVAL_TICKS = 4L;

    private static final Map<UUID, Long> lastSoundTick = new HashMap<>();

    /** Claims this player's rotation-sound allowance; the rotation itself has already happened. */
    public static boolean claimRotation(ServerPlayer player) {
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

    public static final float VOLUME = 0.5f;
    private static final float ROTATION_PITCH_BASE = 0.9f;
    private static final float ROTATION_PITCH_SPREAD = 0.2f;

    /** Broadcasts a slightly pitch-varied rotation sound if the player's sound throttle permits it. */
    public static void playRotation(ServerPlayer sp, BlockPos pos, SoundEvent sound) {
        if (!claimRotation(sp)) {
            return;
        }
        float pitch = ROTATION_PITCH_BASE
                + sp.serverLevel().getRandom().nextFloat() * ROTATION_PITCH_SPREAD;
        sp.serverLevel().playSound(null, pos, sound, SoundSource.BLOCKS, VOLUME, pitch);
    }
}
