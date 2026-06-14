package com.github.crittscott.somestacks;

import com.github.crittscott.somestacks.block.BarStackBlock;
import com.github.crittscott.somestacks.block.BarStackBE;
import com.github.crittscott.somestacks.block.SinglesStackBlock;
import com.github.crittscott.somestacks.block.SinglesStackBE;
import com.github.crittscott.somestacks.block.StorageStackBlock;
import com.github.crittscott.somestacks.block.StorageStackBE;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModRegistry {
    private ModRegistry() {}

    public static final DeferredRegister<Block> BLOCKS =
            DeferredRegister.create(ForgeRegistries.BLOCKS, SomeStacks.MODID);
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES, SomeStacks.MODID);

    public static final RegistryObject<Block> STORAGE_STACK_BLOCK = BLOCKS.register("storage_stack_block",
            () -> new StorageStackBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.METAL)
                    .strength(0.5F, 6.0F)
                    .pushReaction(PushReaction.NORMAL)
                    .noOcclusion()
            ));

    public static final RegistryObject<Block> SINGLES_STACK_BLOCK = BLOCKS.register("singles_stack_block",
            () -> new SinglesStackBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.WOOD)
                    .strength(0.5F, 6.0F)
                    .pushReaction(PushReaction.NORMAL)
                    .noOcclusion()
            ));

    public static final RegistryObject<Block> BAR_STACK_BLOCK = BLOCKS.register("bar_stack_block",
            () -> new BarStackBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.METAL)
                    .strength(0.5F, 6.0F)
                    .pushReaction(PushReaction.NORMAL)
                    .noOcclusion()
            ));

    public static final RegistryObject<BlockEntityType<StorageStackBE>> STACK_BE =
            BLOCK_ENTITIES.register("stack_be",
                    () -> BlockEntityType.Builder.of(StorageStackBE::new, STORAGE_STACK_BLOCK.get()).build(null));

    public static final RegistryObject<BlockEntityType<SinglesStackBE>> SINGLES_STACK_BE =
            BLOCK_ENTITIES.register("singles_stack_be",
                    () -> BlockEntityType.Builder.of(SinglesStackBE::new, SINGLES_STACK_BLOCK.get()).build(null));

    public static final RegistryObject<BlockEntityType<BarStackBE>> BAR_STACK_BE =
            BLOCK_ENTITIES.register("bar_stack_be",
                    () -> BlockEntityType.Builder.of(BarStackBE::new, BAR_STACK_BLOCK.get()).build(null));

    public static void init(IEventBus modBus) {
        BLOCKS.register(modBus);
        BLOCK_ENTITIES.register(modBus);
    }
}
