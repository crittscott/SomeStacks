package com.github.crittscott.somestacks;

import com.github.crittscott.somestacks.block.BarStackBE;
import com.github.crittscott.somestacks.block.BarStackBlock;
import com.github.crittscott.somestacks.block.SinglesStackBE;
import com.github.crittscott.somestacks.block.SinglesStackBlock;
import com.github.crittscott.somestacks.block.StackBlock;
import com.github.crittscott.somestacks.block.StorageStackBE;
import com.github.crittscott.somestacks.block.StorageStackBlock;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.sounds.SoundEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

import java.util.Set;

/**
 * The mod's registered blocks and block entity types, and the Forge registration glue behind them.
 * Common code never registers anything itself; {@link CommonRegistry} is populated below so it can
 * still read back what got registered.
 *
 * <p>There are deliberately no block items, no recipes, and no menus: stacks reach the world only
 * through a player gesture or capability-driven growth, and their contents are reached by clicking
 * the rendered cells rather than by opening a screen.
 *
 * <p>Singles and Bar declare a dynamic shape, because theirs follows their contents. All three
 * block piston movement, since a moved stack would leave its block entity behind.
 */
public final class ModRegistry {
    private ModRegistry() {}

    public static final DeferredRegister<Block> BLOCKS =
            DeferredRegister.create(ForgeRegistries.BLOCKS, SomeStacks.MODID);
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES, SomeStacks.MODID);
    public static final DeferredRegister<SoundEvent> SOUNDS =
            DeferredRegister.create(ForgeRegistries.SOUND_EVENTS, SomeStacks.MODID);

    public static final RegistryObject<Block> BAR_STACK_BLOCK = BLOCKS.register(CommonRegistry.BAR_STACK_BLOCK_PATH,
            () -> new BarStackBlock(StackBlock.barProperties().setId(blockKey(CommonRegistry.BAR_STACK_BLOCK_PATH))));

    public static final RegistryObject<Block> SINGLES_STACK_BLOCK = BLOCKS.register(CommonRegistry.SINGLES_STACK_BLOCK_PATH,
            () -> new SinglesStackBlock(StackBlock.singlesProperties().setId(blockKey(CommonRegistry.SINGLES_STACK_BLOCK_PATH))));

    public static final RegistryObject<Block> STORAGE_STACK_BLOCK = BLOCKS.register(CommonRegistry.STORAGE_STACK_BLOCK_PATH,
            () -> new StorageStackBlock(StackBlock.storageProperties().setId(blockKey(CommonRegistry.STORAGE_STACK_BLOCK_PATH))));

    public static final RegistryObject<SoundEvent> STORAGE_DEPOSIT_SOUND = sound(CommonRegistry.STORAGE_DEPOSIT_SOUND_PATH);
    public static final RegistryObject<SoundEvent> STORAGE_EXTRACT_SOUND = sound(CommonRegistry.STORAGE_EXTRACT_SOUND_PATH);
    public static final RegistryObject<SoundEvent> STORAGE_ROTATE_SOUND = sound(CommonRegistry.STORAGE_ROTATE_SOUND_PATH);
    public static final RegistryObject<SoundEvent> SINGLES_DEPOSIT_SOUND = sound(CommonRegistry.SINGLES_DEPOSIT_SOUND_PATH);
    public static final RegistryObject<SoundEvent> SINGLES_EXTRACT_SOUND = sound(CommonRegistry.SINGLES_EXTRACT_SOUND_PATH);
    public static final RegistryObject<SoundEvent> SINGLES_ROTATE_SOUND = sound(CommonRegistry.SINGLES_ROTATE_SOUND_PATH);
    public static final RegistryObject<SoundEvent> SINGLES_ROTATE_ITEM_SOUND = sound(CommonRegistry.SINGLES_ROTATE_ITEM_SOUND_PATH);
    public static final RegistryObject<SoundEvent> BAR_DEPOSIT_SOUND = sound(CommonRegistry.BAR_DEPOSIT_SOUND_PATH);
    public static final RegistryObject<SoundEvent> BAR_EXTRACT_SOUND = sound(CommonRegistry.BAR_EXTRACT_SOUND_PATH);

    public static final RegistryObject<BlockEntityType<BarStackBE>> BAR_STACK_BE =
            BLOCK_ENTITIES.register(CommonRegistry.BAR_STACK_BE_PATH,
                    () -> new BlockEntityType<>(BarStackBE::new, Set.of(BAR_STACK_BLOCK.get())));

    public static final RegistryObject<BlockEntityType<SinglesStackBE>> SINGLES_STACK_BE =
            BLOCK_ENTITIES.register(CommonRegistry.SINGLES_STACK_BE_PATH,
                    () -> new BlockEntityType<>(SinglesStackBE::new, Set.of(SINGLES_STACK_BLOCK.get())));

    public static final RegistryObject<BlockEntityType<StorageStackBE>> STORAGE_STACK_BE =
            BLOCK_ENTITIES.register(CommonRegistry.STORAGE_STACK_BE_PATH,
                    () -> new BlockEntityType<>(StorageStackBE::new, Set.of(STORAGE_STACK_BLOCK.get())));

    static {
        // Populated eagerly, not lazily: the method references are cheap and deferred by
        // RegistryObject::get itself, and setting them here keeps every common-side caller safe
        // the moment this class is touched, without depending on init() having run first.
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

    private static ResourceKey<Block> blockKey(String path) {
        return ResourceKey.create(Registries.BLOCK, ResourceLocation.fromNamespaceAndPath(SomeStacks.MODID, path));
    }

    private static RegistryObject<SoundEvent> sound(String path) {
        ResourceLocation id = ResourceLocation.fromNamespaceAndPath(SomeStacks.MODID, path);
        return SOUNDS.register(path, () -> SoundEvent.createVariableRangeEvent(id));
    }

    public static void init(IEventBus modBus) {
        BLOCKS.register(modBus);
        BLOCK_ENTITIES.register(modBus);
        SOUNDS.register(modBus);
    }
}
