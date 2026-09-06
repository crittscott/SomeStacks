package com.github.crittscott.somestacks.server;

import net.minecraft.server.level.ServerPlayer;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Enforces one stack gesture per player per tick and separately rate-limits rotation sounds.
 *
 * <p>The client matches at most one interaction rule per click and cancels the off-hand pass, but
 * custom packets bypass vanilla's interaction pacing. The server therefore restores that limit at
 * the packet boundary. An attempt spends the tick even if later validation rejects it.
 *
 * <p>Rotation remains available every tick, but its sound is limited to once every four ticks so a
 * held gesture does not broadcast twenty sounds per second.
 */
public final class GestureThrottle {
    private GestureThrottle() {}

    /** Minimum ticks between rotation sounds initiated by the same player. */
    private static final long ROTATION_SOUND_INTERVAL = 4L;

    /** Last gesture-attempt tick by player. */
    private static final Map<UUID, Long> lastGestureTick = new HashMap<>();

    /** Last rotation-sound tick by initiating player. */
    private static final Map<UUID, Long> lastRotationSoundTick = new HashMap<>();

    /**
     * Claims this player's gesture allowance for the current tick.
     *
     * @return whether the gesture may proceed
     */
    public static boolean claimTick(ServerPlayer sp) {
        return claimTick(sp.getUUID(), sp.serverLevel().getGameTime());
    }

    /** Claims a gesture allowance for an explicit identity and game tick. */
    public static boolean claimTick(UUID id, long tick) {
        Long last = lastGestureTick.put(id, tick);
        return last == null || last != tick;
    }

    /**
     * Claims this player's rotation-sound allowance. This does not gate the rotation, which the
     * caller has already completed.
     *
     * @return whether the sound may play
     */
    public static boolean claimRotationSound(ServerPlayer sp) {
        return claimRotationSound(sp.getUUID(), sp.serverLevel().getGameTime());
    }

    /** Claims a rotation-sound allowance for an explicit identity and game tick. */
    public static boolean claimRotationSound(UUID id, long tick) {
        Long last = lastRotationSoundTick.get(id);
        if (last != null && tick - last < ROTATION_SOUND_INTERVAL) {
            return false;
        }
        lastRotationSoundTick.put(id, tick);
        return true;
    }

    public static void clear(UUID id) {
        lastGestureTick.remove(id);
        lastRotationSoundTick.remove(id);
    }
}
