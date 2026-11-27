package com.github.crittscott.somestacks;

import com.github.crittscott.somestacks.block.BarStackBE;
import com.github.crittscott.somestacks.block.BarStackBlock;
import com.github.crittscott.somestacks.block.RunItemHandler;
import com.github.crittscott.somestacks.block.SinglesStackBE;
import com.github.crittscott.somestacks.block.SinglesStackBlock;
import com.github.crittscott.somestacks.block.StackBlock;
import com.github.crittscott.somestacks.block.StorageStackBE;
import com.github.crittscott.somestacks.block.StorageStackBlock;

import net.minecraft.core.registries.Registries;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.Set;

/**
 * The mod's registered blocks, block entity types, and sounds, the NeoForge registration glue
 * behind them, and the block entities' item-handler capability. Common code reads the registered
 * values through {@link CommonRegistry}.
 */
public final class ModRegistry {
    private ModRegistry() {}

    public static final DeferredRegister.Blocks BLOCKS =
            DeferredRegister.createBlocks(SomeStacksNeoForge.MODID);
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, SomeStacksNeoForge.MODID);
    public static final DeferredRegister<SoundEvent> SOUNDS =
            DeferredRegister.create(Registries.SOUND_EVENT, SomeStacksNeoForge.MODID);

    public static final DeferredBlock<BarStackBlock> BAR_STACK_BLOCK =
            BLOCKS.registerBlock(
                    CommonRegistry.BAR_STACK_BLOCK.getPath(),
                    BarStackBlock::new,
                    StackBlock.barProperties());

    public static final DeferredBlock<SinglesStackBlock> SINGLES_STACK_BLOCK =
            BLOCKS.registerBlock(
                    CommonRegistry.SINGLES_STACK_BLOCK.getPath(),
                    SinglesStackBlock::new,
                    StackBlock.singlesProperties());

    public static final DeferredBlock<StorageStackBlock> STORAGE_STACK_BLOCK =
            BLOCKS.registerBlock(
                    CommonRegistry.STORAGE_STACK_BLOCK.getPath(),
                    StorageStackBlock::new,
                    StackBlock.storageProperties());

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<BarStackBE>>
            BAR_STACK_BE =
                    BLOCK_ENTITIES.register(
                            CommonRegistry.BAR_STACK_BE.getPath(),
                            () ->
                                    new BlockEntityType<>(
                                            BarStackBE::new, Set.of(BAR_STACK_BLOCK.get())));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<SinglesStackBE>>
            SINGLES_STACK_BE =
                    BLOCK_ENTITIES.register(
                            CommonRegistry.SINGLES_STACK_BE.getPath(),
                            () ->
                                    new BlockEntityType<>(
                                            SinglesStackBE::new,
                                            Set.of(SINGLES_STACK_BLOCK.get())));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<StorageStackBE>>
            STORAGE_STACK_BE =
                    BLOCK_ENTITIES.register(
                            CommonRegistry.STORAGE_STACK_BE.getPath(),
                            () ->
                                    new BlockEntityType<>(
                                            StorageStackBE::new,
                                            Set.of(STORAGE_STACK_BLOCK.get())));

    /**
     * Subscribes the block, block entity, and sound registers (after adding every sound) and the
     * capability registration to the mod bus. Runs once from the mod constructor.
     */
    public static void init(IEventBus modBus) {
        BLOCKS.register(modBus);
        BLOCK_ENTITIES.register(modBus);
        for (CommonRegistry.Sound sound : CommonRegistry.Sound.values()) {
            SOUNDS.register(
                    sound.id().getPath(), () -> SoundEvent.createVariableRangeEvent(sound.id()));
        }
        SOUNDS.register(modBus);
        modBus.addListener(ModRegistry::onRegisterCapabilities);
    }

    /**
     * Exposes {@code Capabilities.ItemHandler.BLOCK} on the three block entity types. Each provider
     * hands back a {@link RunItemHandler} whole-run view, so a machine on any block of a run sees
     * the entire run. The handler reads its run live on every call, so a run growing or shrinking
     * does not stale it.
     */
    private static void onRegisterCapabilities(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(
                Capabilities.ItemHandler.BLOCK,
                STORAGE_STACK_BE.get(),
                (blockEntity, side) -> new RunItemHandler(blockEntity));
        event.registerBlockEntity(
                Capabilities.ItemHandler.BLOCK,
                SINGLES_STACK_BE.get(),
                (blockEntity, side) -> new RunItemHandler(blockEntity));
        event.registerBlockEntity(
                Capabilities.ItemHandler.BLOCK,
                BAR_STACK_BE.get(),
                (blockEntity, side) -> new RunItemHandler(blockEntity));
    }
}
