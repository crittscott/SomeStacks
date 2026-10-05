package com.github.crittscott.somestacks;

import com.github.crittscott.somestacks.block.BarStackBE;
import com.github.crittscott.somestacks.block.BarStackBlock;
import com.github.crittscott.somestacks.block.SinglesStackBE;
import com.github.crittscott.somestacks.block.SinglesStackBlock;
import com.github.crittscott.somestacks.block.StackBlock;
import com.github.crittscott.somestacks.block.StorageStackBE;
import com.github.crittscott.somestacks.block.StorageStackBlock;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.Set;

/**
 * The mod's registered blocks and block entity types, and the NeoForge registration glue behind
 * them. Common code never registers anything itself; {@link CommonRegistry} is populated below so it
 * can still read back what got registered.
 */
public final class ModRegistry {
    private ModRegistry() {}

    public static final DeferredRegister.Blocks BLOCKS =
            DeferredRegister.createBlocks(SomeStacksNeoForge.MODID);
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, SomeStacksNeoForge.MODID);
    public static final DeferredRegister<SoundEvent> SOUNDS =
            DeferredRegister.create(Registries.SOUND_EVENT, SomeStacksNeoForge.MODID);

    public static final DeferredBlock<BarStackBlock> BAR_STACK_BLOCK = BLOCKS.registerBlock(CommonRegistry.BAR_STACK_BLOCK_PATH, BarStackBlock::new,
            StackBlock.barProperties());

    public static final DeferredBlock<SinglesStackBlock> SINGLES_STACK_BLOCK = BLOCKS.registerBlock(CommonRegistry.SINGLES_STACK_BLOCK_PATH, SinglesStackBlock::new,
            StackBlock.singlesProperties());

    public static final DeferredBlock<StorageStackBlock> STORAGE_STACK_BLOCK = BLOCKS.registerBlock(CommonRegistry.STORAGE_STACK_BLOCK_PATH, StorageStackBlock::new,
            StackBlock.storageProperties());

    public static final DeferredHolder<SoundEvent, SoundEvent> STORAGE_DEPOSIT_SOUND = sound(CommonRegistry.STORAGE_DEPOSIT_SOUND_PATH);
    public static final DeferredHolder<SoundEvent, SoundEvent> STORAGE_EXTRACT_SOUND = sound(CommonRegistry.STORAGE_EXTRACT_SOUND_PATH);
    public static final DeferredHolder<SoundEvent, SoundEvent> STORAGE_ROTATE_SOUND = sound(CommonRegistry.STORAGE_ROTATE_SOUND_PATH);
    public static final DeferredHolder<SoundEvent, SoundEvent> SINGLES_DEPOSIT_SOUND = sound(CommonRegistry.SINGLES_DEPOSIT_SOUND_PATH);
    public static final DeferredHolder<SoundEvent, SoundEvent> SINGLES_EXTRACT_SOUND = sound(CommonRegistry.SINGLES_EXTRACT_SOUND_PATH);
    public static final DeferredHolder<SoundEvent, SoundEvent> SINGLES_ROTATE_SOUND = sound(CommonRegistry.SINGLES_ROTATE_SOUND_PATH);
    public static final DeferredHolder<SoundEvent, SoundEvent> SINGLES_ROTATE_ITEM_SOUND = sound(CommonRegistry.SINGLES_ROTATE_ITEM_SOUND_PATH);
    public static final DeferredHolder<SoundEvent, SoundEvent> BAR_DEPOSIT_SOUND = sound(CommonRegistry.BAR_DEPOSIT_SOUND_PATH);
    public static final DeferredHolder<SoundEvent, SoundEvent> BAR_EXTRACT_SOUND = sound(CommonRegistry.BAR_EXTRACT_SOUND_PATH);

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<BarStackBE>> BAR_STACK_BE =
            BLOCK_ENTITIES.register(CommonRegistry.BAR_STACK_BE_PATH,
                    () -> new BlockEntityType<>(BarStackBE::new, Set.of(BAR_STACK_BLOCK.get())));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<SinglesStackBE>> SINGLES_STACK_BE =
            BLOCK_ENTITIES.register(CommonRegistry.SINGLES_STACK_BE_PATH,
                    () -> new BlockEntityType<>(SinglesStackBE::new, Set.of(SINGLES_STACK_BLOCK.get())));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<StorageStackBE>> STORAGE_STACK_BE =
            BLOCK_ENTITIES.register(CommonRegistry.STORAGE_STACK_BE_PATH,
                    () -> new BlockEntityType<>(StorageStackBE::new, Set.of(STORAGE_STACK_BLOCK.get())));

    static {
        CommonRegistry.STORAGE_STACK_BLOCK = STORAGE_STACK_BLOCK::get;
        CommonRegistry.SINGLES_STACK_BLOCK = SINGLES_STACK_BLOCK::get;
        CommonRegistry.BAR_STACK_BLOCK = BAR_STACK_BLOCK::get;
        CommonRegistry.STORAGE_STACK_BE = STORAGE_STACK_BE::get;
        CommonRegistry.SINGLES_STACK_BE = SINGLES_STACK_BE::get;
        CommonRegistry.BAR_STACK_BE = BAR_STACK_BE::get;
        CommonRegistry.STORAGE_DEPOSIT_SOUND = STORAGE_DEPOSIT_SOUND::get;
        CommonRegistry.STORAGE_EXTRACT_SOUND = STORAGE_EXTRACT_SOUND::get;
        CommonRegistry.STORAGE_ROTATE_SOUND = STORAGE_ROTATE_SOUND::get;
        CommonRegistry.SINGLES_DEPOSIT_SOUND = SINGLES_DEPOSIT_SOUND::get;
        CommonRegistry.SINGLES_EXTRACT_SOUND = SINGLES_EXTRACT_SOUND::get;
        CommonRegistry.SINGLES_ROTATE_SOUND = SINGLES_ROTATE_SOUND::get;
        CommonRegistry.SINGLES_ROTATE_ITEM_SOUND = SINGLES_ROTATE_ITEM_SOUND::get;
        CommonRegistry.BAR_DEPOSIT_SOUND = BAR_DEPOSIT_SOUND::get;
        CommonRegistry.BAR_EXTRACT_SOUND = BAR_EXTRACT_SOUND::get;
    }

    private static DeferredHolder<SoundEvent, SoundEvent> sound(String path) {
        ResourceLocation id = ResourceLocation.fromNamespaceAndPath(SomeStacksNeoForge.MODID, path);
        return SOUNDS.register(path, () -> SoundEvent.createVariableRangeEvent(id));
    }

    public static void init(IEventBus modBus) {
        BLOCKS.register(modBus);
        BLOCK_ENTITIES.register(modBus);
        SOUNDS.register(modBus);
    }
}
