package com.github.crittscott.somestacks;

import com.github.crittscott.somestacks.block.BarStackBE;
import com.github.crittscott.somestacks.block.SinglesStackBE;
import com.github.crittscott.somestacks.block.StorageStackBE;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;

/** Shared identifiers and access to values after loader-native registration completes. */
public final class CommonRegistry {
    private CommonRegistry() {}

    public static final Identifier STORAGE_STACK_BLOCK = id("storage_stack_block");
    public static final Identifier SINGLES_STACK_BLOCK = id("singles_stack_block");
    public static final Identifier BAR_STACK_BLOCK = id("bar_stack_block");
    public static final Identifier STORAGE_STACK_BE = id("stack_be");
    public static final Identifier SINGLES_STACK_BE = id("singles_stack_be");
    public static final Identifier BAR_STACK_BE = id("bar_stack_be");
    public static final Identifier STORAGE_DEPOSIT_SOUND = id("block.storage_stack.deposit");
    public static final Identifier STORAGE_EXTRACT_SOUND = id("block.storage_stack.extract");
    public static final Identifier STORAGE_ROTATE_SOUND = id("block.storage_stack.rotate");
    public static final Identifier SINGLES_DEPOSIT_SOUND = id("block.singles_stack.deposit");
    public static final Identifier SINGLES_EXTRACT_SOUND = id("block.singles_stack.extract");
    public static final Identifier SINGLES_ROTATE_SOUND = id("block.singles_stack.rotate");
    public static final Identifier SINGLES_ROTATE_ITEM_SOUND = id("block.singles_stack.rotate_item");
    public static final Identifier BAR_DEPOSIT_SOUND = id("block.bar_stack.deposit");
    public static final Identifier BAR_EXTRACT_SOUND = id("block.bar_stack.extract");

    public static Block storageStackBlock() {
        return lookup(BuiltInRegistries.BLOCK, STORAGE_STACK_BLOCK);
    }

    public static Block singlesStackBlock() {
        return lookup(BuiltInRegistries.BLOCK, SINGLES_STACK_BLOCK);
    }

    public static Block barStackBlock() {
        return lookup(BuiltInRegistries.BLOCK, BAR_STACK_BLOCK);
    }

    public static BlockEntityType<StorageStackBE> storageStackBe() {
        return blockEntityType(STORAGE_STACK_BE);
    }

    public static BlockEntityType<SinglesStackBE> singlesStackBe() {
        return blockEntityType(SINGLES_STACK_BE);
    }

    public static BlockEntityType<BarStackBE> barStackBe() {
        return blockEntityType(BAR_STACK_BE);
    }

    public static SoundEvent storageDepositSound() {
        return lookup(BuiltInRegistries.SOUND_EVENT, STORAGE_DEPOSIT_SOUND);
    }

    public static SoundEvent storageExtractSound() {
        return lookup(BuiltInRegistries.SOUND_EVENT, STORAGE_EXTRACT_SOUND);
    }

    public static SoundEvent storageRotateSound() {
        return lookup(BuiltInRegistries.SOUND_EVENT, STORAGE_ROTATE_SOUND);
    }

    public static SoundEvent singlesDepositSound() {
        return lookup(BuiltInRegistries.SOUND_EVENT, SINGLES_DEPOSIT_SOUND);
    }

    public static SoundEvent singlesExtractSound() {
        return lookup(BuiltInRegistries.SOUND_EVENT, SINGLES_EXTRACT_SOUND);
    }

    public static SoundEvent singlesRotateSound() {
        return lookup(BuiltInRegistries.SOUND_EVENT, SINGLES_ROTATE_SOUND);
    }

    public static SoundEvent singlesRotateItemSound() {
        return lookup(BuiltInRegistries.SOUND_EVENT, SINGLES_ROTATE_ITEM_SOUND);
    }

    public static SoundEvent barDepositSound() {
        return lookup(BuiltInRegistries.SOUND_EVENT, BAR_DEPOSIT_SOUND);
    }

    public static SoundEvent barExtractSound() {
        return lookup(BuiltInRegistries.SOUND_EVENT, BAR_EXTRACT_SOUND);
    }

    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(SomeStacksCommon.MODID, path);
    }

    private static <T> T lookup(Registry<T> registry, Identifier id) {
        return registry.getValue(id);
    }

    @SuppressWarnings("unchecked")
    private static <T extends BlockEntity> BlockEntityType<T> blockEntityType(Identifier id) {
        return (BlockEntityType<T>) lookup(BuiltInRegistries.BLOCK_ENTITY_TYPE, id);
    }
}
