package com.github.crittscott.somestacks.server;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;

/**
 * The sound each stack block plays for each action, resolved on the logical server by
 * {@link StackSoundData} and read by the packet handlers that perform the action.
 *
 * <p>The mod registers no sound events of its own, so every value here names one already in the
 * sound registry. The initial values are the same wood sounds the bundled data names, and they hold
 * until the first data pack load.
 */
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

    /** The volume every stack sound plays at. */
    public static final float VOLUME = 0.5f;

    /**
     * Plays a rotation for the player who turned something, if their rotations may be heard yet.
     *
     * <p>The pitch is nudged each time so that a fast sequence reads as a series of turns rather
     * than as one sound repeating.
     */
    public static void playRotation(ServerPlayer sp, BlockPos pos, SoundEvent sound) {
        if (!GestureThrottle.claimRotationSound(sp)) {
            return;
        }
        float pitch = 0.9f + sp.serverLevel().getRandom().nextFloat() * 0.2f;
        sp.serverLevel().playSound(null, pos, sound, SoundSource.BLOCKS, VOLUME, pitch);
    }
}
