package com.github.crittscott.somestacks.server;

import com.github.crittscott.somestacks.CommonRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;

/** Loader-neutral access to the registered stack action sounds. */
public final class StackSounds {
    private StackSounds() {}

    public static final float VOLUME = 0.5f;
    private static final float ROTATION_PITCH_BASE = 0.9f;
    private static final float ROTATION_PITCH_SPREAD = 0.2f;

    public static SoundEvent storageDeposit() { return CommonRegistry.STORAGE_DEPOSIT_SOUND.get(); }
    public static SoundEvent storageExtract() { return CommonRegistry.STORAGE_EXTRACT_SOUND.get(); }
    public static SoundEvent storageRotate() { return CommonRegistry.STORAGE_ROTATE_SOUND.get(); }
    public static SoundEvent singlesDeposit() { return CommonRegistry.SINGLES_DEPOSIT_SOUND.get(); }
    public static SoundEvent singlesExtract() { return CommonRegistry.SINGLES_EXTRACT_SOUND.get(); }
    public static SoundEvent singlesRotate() { return CommonRegistry.SINGLES_ROTATE_SOUND.get(); }
    public static SoundEvent singlesRotateItem() { return CommonRegistry.SINGLES_ROTATE_ITEM_SOUND.get(); }
    public static SoundEvent barDeposit() { return CommonRegistry.BAR_DEPOSIT_SOUND.get(); }
    public static SoundEvent barExtract() { return CommonRegistry.BAR_EXTRACT_SOUND.get(); }

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
