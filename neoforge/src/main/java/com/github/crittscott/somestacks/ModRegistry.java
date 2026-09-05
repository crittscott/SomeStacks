package com.github.crittscott.somestacks;

import com.github.crittscott.somestacks.block.BarStackBE;
import com.github.crittscott.somestacks.block.BarStackBlock;
import com.github.crittscott.somestacks.block.SinglesStackBE;
import com.github.crittscott.somestacks.block.SinglesStackBlock;
import com.github.crittscott.somestacks.block.StorageStackBE;
import com.github.crittscott.somestacks.block.StorageStackBlock;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

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

    public static final DeferredBlock<BarStackBlock> BAR_STACK_BLOCK = BLOCKS.register("bar_stack_block",
            () -> new BarStackBlock(BlockBehaviour.Properties.of()
                    .dynamicShape()
                    .mapColor(MapColor.METAL)
                    .noOcclusion()
                    .pushReaction(PushReaction.BLOCK)
                    .strength(0.5F, 6.0F)
                    .lightLevel(state -> state.getValue(BarStackBlock.LIGHT_LEVEL))));

    public static final DeferredBlock<SinglesStackBlock> SINGLES_STACK_BLOCK = BLOCKS.register("singles_stack_block",
            () -> new SinglesStackBlock(BlockBehaviour.Properties.of()
                    .dynamicShape()
                    .mapColor(MapColor.WOOD)
                    .noOcclusion()
                    .pushReaction(PushReaction.BLOCK)
                    .strength(0.5F, 6.0F)
                    .lightLevel(state -> state.getValue(SinglesStackBlock.LIGHT_LEVEL))));

    public static final DeferredBlock<StorageStackBlock> STORAGE_STACK_BLOCK = BLOCKS.register("storage_stack_block",
            () -> new StorageStackBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.METAL)
                    .noOcclusion()
                    .pushReaction(PushReaction.BLOCK)
                    .strength(0.5F, 6.0F)
                    .lightLevel(state -> state.getValue(StorageStackBlock.LIGHT_LEVEL))));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<BarStackBE>> BAR_STACK_BE =
            BLOCK_ENTITIES.register("bar_stack_be",
                    () -> BlockEntityType.Builder.of(BarStackBE::new, BAR_STACK_BLOCK.get()).build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<SinglesStackBE>> SINGLES_STACK_BE =
            BLOCK_ENTITIES.register("singles_stack_be",
                    () -> BlockEntityType.Builder.of(SinglesStackBE::new, SINGLES_STACK_BLOCK.get()).build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<StorageStackBE>> STORAGE_STACK_BE =
            BLOCK_ENTITIES.register("stack_be",
                    () -> BlockEntityType.Builder.of(StorageStackBE::new, STORAGE_STACK_BLOCK.get()).build(null));

    static {
        CommonRegistry.STORAGE_STACK_BLOCK = STORAGE_STACK_BLOCK::get;
        CommonRegistry.SINGLES_STACK_BLOCK = SINGLES_STACK_BLOCK::get;
        CommonRegistry.BAR_STACK_BLOCK = BAR_STACK_BLOCK::get;
        CommonRegistry.STORAGE_STACK_BE = STORAGE_STACK_BE::get;
        CommonRegistry.SINGLES_STACK_BE = SINGLES_STACK_BE::get;
        CommonRegistry.BAR_STACK_BE = BAR_STACK_BE::get;
    }

    public static void init(IEventBus modBus) {
        BLOCKS.register(modBus);
        BLOCK_ENTITIES.register(modBus);
    }
}
