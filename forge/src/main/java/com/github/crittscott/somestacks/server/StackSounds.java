package com.github.crittscott.somestacks.server;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;

/** Current server-side stack sounds, populated by {@link StackSoundData}. */
public final class StackSounds {
    private StackSounds() {}

    public static SoundEvent BAR_DEPOSIT = SoundEvents.WOOD_PLACE;
    public static SoundEvent BAR_EXTRACT = SoundEvents.WOOD_BREAK;
    public static SoundEvent SINGLES_DEPOSIT = SoundEvents.WOOD_PLACE;
    public static SoundEvent SINGLES_EXTRACT = SoundEvents.WOOD_BREAK;
    public static SoundEvent SINGLES_ROTATE = SoundEvents.WOOD_HIT;
    public static SoundEvent SINGLES_ROTATE_ITEM = SoundEvents.WOOD_HIT;
    public static SoundEvent STORAGE_DEPOSIT = SoundEvents.WOOD_PLACE;
    public static SoundEvent STORAGE_EXTRACT = SoundEvents.WOOD_BREAK;
    public static SoundEvent STORAGE_ROTATE = SoundEvents.WOOD_HIT;

    public static final float VOLUME = 0.5f;

    /** Broadcasts a slightly pitch-varied rotation sound if the player's sound throttle permits it. */
    public static void playRotation(ServerPlayer sp, BlockPos pos, SoundEvent sound) {
        if (!GestureThrottle.claimRotationSound(sp)) {
            return;
        }
        float pitch = 0.9f + sp.serverLevel().getRandom().nextFloat() * 0.2f;
        sp.serverLevel().playSound(null, pos, sound, SoundSource.BLOCKS, VOLUME, pitch);
    }
}
