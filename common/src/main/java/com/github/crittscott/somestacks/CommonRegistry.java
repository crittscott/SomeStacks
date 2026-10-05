package com.github.crittscott.somestacks;

import com.github.crittscott.somestacks.block.BarStackBE;
import com.github.crittscott.somestacks.block.SinglesStackBE;
import com.github.crittscott.somestacks.block.StorageStackBE;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.sounds.SoundEvent;

import java.util.function.Supplier;

/**
 * Loader-neutral access to registered blocks, block entity types, and action sounds. Each loader's
 * registration glue assigns these once registration is set up; common code only reads them back.
 */
public final class CommonRegistry {
    private CommonRegistry() {
    }

    public static final String STORAGE_STACK_BLOCK_PATH = "storage_stack_block";
    public static final String SINGLES_STACK_BLOCK_PATH = "singles_stack_block";
    public static final String BAR_STACK_BLOCK_PATH = "bar_stack_block";
    public static final String STORAGE_STACK_BE_PATH = "stack_be";
    public static final String SINGLES_STACK_BE_PATH = "singles_stack_be";
    public static final String BAR_STACK_BE_PATH = "bar_stack_be";

    public static final String STORAGE_DEPOSIT_SOUND_PATH = "block.storage_stack.deposit";
    public static final String STORAGE_EXTRACT_SOUND_PATH = "block.storage_stack.extract";
    public static final String STORAGE_ROTATE_SOUND_PATH = "block.storage_stack.rotate";
    public static final String SINGLES_DEPOSIT_SOUND_PATH = "block.singles_stack.deposit";
    public static final String SINGLES_EXTRACT_SOUND_PATH = "block.singles_stack.extract";
    public static final String SINGLES_ROTATE_SOUND_PATH = "block.singles_stack.rotate";
    public static final String SINGLES_ROTATE_ITEM_SOUND_PATH = "block.singles_stack.rotate_item";
    public static final String BAR_DEPOSIT_SOUND_PATH = "block.bar_stack.deposit";
    public static final String BAR_EXTRACT_SOUND_PATH = "block.bar_stack.extract";

    public static Supplier<Block> STORAGE_STACK_BLOCK;
    public static Supplier<Block> SINGLES_STACK_BLOCK;
    public static Supplier<Block> BAR_STACK_BLOCK;

    public static Supplier<BlockEntityType<StorageStackBE>> STORAGE_STACK_BE;
    public static Supplier<BlockEntityType<SinglesStackBE>> SINGLES_STACK_BE;
    public static Supplier<BlockEntityType<BarStackBE>> BAR_STACK_BE;

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
