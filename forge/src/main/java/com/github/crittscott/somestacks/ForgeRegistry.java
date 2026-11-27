package com.github.crittscott.somestacks;

import com.github.crittscott.somestacks.block.BarStackBE;
import com.github.crittscott.somestacks.block.BarStackBlock;
import com.github.crittscott.somestacks.block.SinglesStackBE;
import com.github.crittscott.somestacks.block.SinglesStackBlock;
import com.github.crittscott.somestacks.block.StorageStackBE;
import com.github.crittscott.somestacks.block.StorageStackBlock;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/**
 * The mod's registered blocks, block entity types, and sound events, and the Forge registration
 * glue behind them. Common code never registers anything itself; {@link CommonRegistry} is
 * populated below so it can still read back what got registered.
 */
public final class ForgeRegistry {
    private ForgeRegistry() {}

    public static final DeferredRegister<Block> BLOCKS =
            DeferredRegister.create(ForgeRegistries.BLOCKS, SomeStacksCommon.MODID);
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES, SomeStacksCommon.MODID);
    public static final DeferredRegister<SoundEvent> SOUNDS =
            DeferredRegister.create(ForgeRegistries.SOUND_EVENTS, SomeStacksCommon.MODID);

    public static final RegistryObject<Block> BAR_STACK_BLOCK =
            BLOCKS.register(
                    CommonRegistry.BAR_STACK_BLOCK_ID,
                    () -> new BarStackBlock(BarStackBlock.properties()));

    public static final RegistryObject<Block> SINGLES_STACK_BLOCK =
            BLOCKS.register(
                    CommonRegistry.SINGLES_STACK_BLOCK_ID,
                    () -> new SinglesStackBlock(SinglesStackBlock.properties()));

    public static final RegistryObject<Block> STORAGE_STACK_BLOCK =
            BLOCKS.register(
                    CommonRegistry.STORAGE_STACK_BLOCK_ID,
                    () -> new StorageStackBlock(StorageStackBlock.properties()));

    public static final RegistryObject<BlockEntityType<BarStackBE>> BAR_STACK_BE =
            BLOCK_ENTITIES.register(
                    CommonRegistry.BAR_STACK_BE_ID,
                    () ->
                            BlockEntityType.Builder.of(BarStackBE::new, BAR_STACK_BLOCK.get())
                                    .build(null));

    public static final RegistryObject<BlockEntityType<SinglesStackBE>> SINGLES_STACK_BE =
            BLOCK_ENTITIES.register(
                    CommonRegistry.SINGLES_STACK_BE_ID,
                    () ->
                            BlockEntityType.Builder.of(
                                            SinglesStackBE::new, SINGLES_STACK_BLOCK.get())
                                    .build(null));

    public static final RegistryObject<BlockEntityType<StorageStackBE>> STORAGE_STACK_BE =
            BLOCK_ENTITIES.register(
                    CommonRegistry.STORAGE_STACK_BE_ID,
                    () ->
                            BlockEntityType.Builder.of(
                                            StorageStackBE::new, STORAGE_STACK_BLOCK.get())
                                    .build(null));

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
        for (CommonRegistry.Sound entry : CommonRegistry.Sound.values()) {
            entry.bind(sound(entry.path())::get);
        }
    }

    public static void init(IEventBus modBus) {
        BLOCKS.register(modBus);
        BLOCK_ENTITIES.register(modBus);
        SOUNDS.register(modBus);
    }

    private static RegistryObject<SoundEvent> sound(String path) {
        return SOUNDS.register(
                path,
                () ->
                        SoundEvent.createVariableRangeEvent(
                                new ResourceLocation(SomeStacksCommon.MODID, path)));
    }
}
