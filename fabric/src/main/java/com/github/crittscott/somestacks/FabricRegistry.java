package com.github.crittscott.somestacks;

import com.github.crittscott.somestacks.block.BarStackBE;
import com.github.crittscott.somestacks.block.BarStackBlock;
import com.github.crittscott.somestacks.block.SinglesStackBE;
import com.github.crittscott.somestacks.block.SinglesStackBlock;
import com.github.crittscott.somestacks.block.StorageStackBE;
import com.github.crittscott.somestacks.block.StorageStackBlock;
import net.fabricmc.fabric.api.object.builder.v1.block.entity.FabricBlockEntityTypeBuilder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;

/** Fabric registration for the shared blocks, block entities, and sound events. */
public final class FabricRegistry {
    private FabricRegistry() {}

    public static final Block BAR_STACK_BLOCK = registerBlock(CommonRegistry.BAR_STACK_BLOCK_ID,
            new BarStackBlock(BarStackBlock.properties()));

    public static final Block SINGLES_STACK_BLOCK = registerBlock(CommonRegistry.SINGLES_STACK_BLOCK_ID,
            new SinglesStackBlock(SinglesStackBlock.properties()));

    public static final Block STORAGE_STACK_BLOCK = registerBlock(CommonRegistry.STORAGE_STACK_BLOCK_ID,
            new StorageStackBlock(StorageStackBlock.properties()));

    public static final BlockEntityType<BarStackBE> BAR_STACK_BE = registerBlockEntity(
            CommonRegistry.BAR_STACK_BE_ID, FabricBlockEntityTypeBuilder.create(BarStackBE::new, BAR_STACK_BLOCK).build());

    public static final BlockEntityType<SinglesStackBE> SINGLES_STACK_BE = registerBlockEntity(
            CommonRegistry.SINGLES_STACK_BE_ID,
            FabricBlockEntityTypeBuilder.create(SinglesStackBE::new, SINGLES_STACK_BLOCK).build());

    public static final BlockEntityType<StorageStackBE> STORAGE_STACK_BE = registerBlockEntity(
            CommonRegistry.STORAGE_STACK_BE_ID,
            FabricBlockEntityTypeBuilder.create(StorageStackBE::new, STORAGE_STACK_BLOCK).build());

    public static final SoundEvent STORAGE_DEPOSIT_SOUND = registerSound(CommonRegistry.STORAGE_DEPOSIT_SOUND_PATH);
    public static final SoundEvent STORAGE_EXTRACT_SOUND = registerSound(CommonRegistry.STORAGE_EXTRACT_SOUND_PATH);
    public static final SoundEvent STORAGE_ROTATE_SOUND = registerSound(CommonRegistry.STORAGE_ROTATE_SOUND_PATH);
    public static final SoundEvent SINGLES_DEPOSIT_SOUND = registerSound(CommonRegistry.SINGLES_DEPOSIT_SOUND_PATH);
    public static final SoundEvent SINGLES_EXTRACT_SOUND = registerSound(CommonRegistry.SINGLES_EXTRACT_SOUND_PATH);
    public static final SoundEvent SINGLES_ROTATE_SOUND = registerSound(CommonRegistry.SINGLES_ROTATE_SOUND_PATH);
    public static final SoundEvent SINGLES_ROTATE_ITEM_SOUND = registerSound(CommonRegistry.SINGLES_ROTATE_ITEM_SOUND_PATH);
    public static final SoundEvent BAR_DEPOSIT_SOUND = registerSound(CommonRegistry.BAR_DEPOSIT_SOUND_PATH);
    public static final SoundEvent BAR_EXTRACT_SOUND = registerSound(CommonRegistry.BAR_EXTRACT_SOUND_PATH);

    public static void init() {
        CommonRegistry.STORAGE_STACK_BLOCK = () -> STORAGE_STACK_BLOCK;
        CommonRegistry.SINGLES_STACK_BLOCK = () -> SINGLES_STACK_BLOCK;
        CommonRegistry.BAR_STACK_BLOCK = () -> BAR_STACK_BLOCK;
        CommonRegistry.STORAGE_STACK_BE = () -> STORAGE_STACK_BE;
        CommonRegistry.SINGLES_STACK_BE = () -> SINGLES_STACK_BE;
        CommonRegistry.BAR_STACK_BE = () -> BAR_STACK_BE;
        CommonRegistry.STORAGE_DEPOSIT_SOUND = () -> STORAGE_DEPOSIT_SOUND;
        CommonRegistry.STORAGE_EXTRACT_SOUND = () -> STORAGE_EXTRACT_SOUND;
        CommonRegistry.STORAGE_ROTATE_SOUND = () -> STORAGE_ROTATE_SOUND;
        CommonRegistry.SINGLES_DEPOSIT_SOUND = () -> SINGLES_DEPOSIT_SOUND;
        CommonRegistry.SINGLES_EXTRACT_SOUND = () -> SINGLES_EXTRACT_SOUND;
        CommonRegistry.SINGLES_ROTATE_SOUND = () -> SINGLES_ROTATE_SOUND;
        CommonRegistry.SINGLES_ROTATE_ITEM_SOUND = () -> SINGLES_ROTATE_ITEM_SOUND;
        CommonRegistry.BAR_DEPOSIT_SOUND = () -> BAR_DEPOSIT_SOUND;
        CommonRegistry.BAR_EXTRACT_SOUND = () -> BAR_EXTRACT_SOUND;
    }

    private static Block registerBlock(String path, Block block) {
        return Registry.register(BuiltInRegistries.BLOCK, id(path), block);
    }

    private static <T extends BlockEntityType<?>> T registerBlockEntity(String path, T type) {
        return Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE, id(path), type);
    }

    private static SoundEvent registerSound(String path) {
        ResourceLocation id = id(path);
        return Registry.register(BuiltInRegistries.SOUND_EVENT, id, SoundEvent.createVariableRangeEvent(id));
    }

    private static ResourceLocation id(String path) {
        return new ResourceLocation(SomeStacksCommon.MODID, path);
    }
}
