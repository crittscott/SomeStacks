package com.github.crittscott.somestacks;

import com.github.crittscott.somestacks.block.BarStackBE;
import com.github.crittscott.somestacks.block.SinglesStackBE;
import com.github.crittscott.somestacks.block.StorageStackBE;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;

import java.util.function.Supplier;

/**
 * Loader-neutral access to the registered blocks, block entity types, and sound events. The loader's own
 * registration glue (Forge's {@code ModRegistry}, or its Fabric equivalent) assigns these once
 * registration is set up; common code never registers anything itself, only reads these back.
 */
public final class CommonRegistry {
    private CommonRegistry() {
    }

    public static Supplier<Block> STORAGE_STACK_BLOCK;
    public static Supplier<Block> SINGLES_STACK_BLOCK;
    public static Supplier<Block> BAR_STACK_BLOCK;

    public static Supplier<BlockEntityType<StorageStackBE>> STORAGE_STACK_BE;
    public static Supplier<BlockEntityType<SinglesStackBE>> SINGLES_STACK_BE;
    public static Supplier<BlockEntityType<BarStackBE>> BAR_STACK_BE;

    /**
     * One sound event per stack action, {@code somestacks:block.<type>.<action>}. Resource packs
     * replace what each plays through {@code assets/somestacks/sounds.json}.
     */
    public static Supplier<SoundEvent> STORAGE_DEPOSIT_SOUND;
    public static Supplier<SoundEvent> STORAGE_EXTRACT_SOUND;
    public static Supplier<SoundEvent> STORAGE_ROTATE_SOUND;
    public static Supplier<SoundEvent> SINGLES_DEPOSIT_SOUND;
    public static Supplier<SoundEvent> SINGLES_EXTRACT_SOUND;
    public static Supplier<SoundEvent> SINGLES_ROTATE_SOUND;
    public static Supplier<SoundEvent> SINGLES_ROTATE_ITEM_SOUND;
    public static Supplier<SoundEvent> BAR_DEPOSIT_SOUND;
    public static Supplier<SoundEvent> BAR_EXTRACT_SOUND;
}
