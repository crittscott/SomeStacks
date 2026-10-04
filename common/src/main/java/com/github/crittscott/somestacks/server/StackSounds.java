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
        float pitch = 0.9f + sp.serverLevel().getRandom().nextFloat() * 0.2f;
        sp.serverLevel().playSound(null, pos, sound, SoundSource.BLOCKS, VOLUME, pitch);
    }
}
