package com.github.crittscott.somestacks.server;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;

/** Stack action sound levels and throttled rotation playback. */
public final class StackSounds {
    private StackSounds() {}

    public static final float VOLUME = 0.5f;
    private static final float ROTATION_PITCH_BASE = 0.9f;
    private static final float ROTATION_PITCH_SPREAD = 0.2f;

    /** Broadcasts a slightly pitch-varied rotation sound if the player's sound throttle permits it. */
    public static void playRotation(ServerPlayer sp, BlockPos pos, SoundEvent sound) {
        if (!RotationSoundThrottle.claim(sp)) {
            return;
        }
        float pitch = ROTATION_PITCH_BASE
                + sp.serverLevel().getRandom().nextFloat() * ROTATION_PITCH_SPREAD;
        sp.serverLevel().playSound(null, pos, sound, SoundSource.BLOCKS, VOLUME, pitch);
    }
}
