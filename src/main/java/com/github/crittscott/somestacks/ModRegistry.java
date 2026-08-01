package com.github.crittscott.somestacks;

import com.github.crittscott.somestacks.block.BarStackBE;
import com.github.crittscott.somestacks.block.BarStackBlock;
import com.github.crittscott.somestacks.block.SinglesStackBE;
import com.github.crittscott.somestacks.block.SinglesStackBlock;
import com.github.crittscott.somestacks.block.StorageStackBE;
import com.github.crittscott.somestacks.block.StorageStackBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/**
 * The mod's registered blocks and block entity types.
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

    public static final RegistryObject<Block> BAR_STACK_BLOCK = BLOCKS.register("bar_stack_block",
            () -> new BarStackBlock(BlockBehaviour.Properties.of()
                    .dynamicShape()
                    .mapColor(MapColor.METAL)
                    .noOcclusion()
                    .pushReaction(PushReaction.BLOCK)
                    .strength(0.5F, 6.0F)
            ));

    public static final RegistryObject<Block> SINGLES_STACK_BLOCK = BLOCKS.register("singles_stack_block",
            () -> new SinglesStackBlock(BlockBehaviour.Properties.of()
                    .dynamicShape()
                    .mapColor(MapColor.WOOD)
                    .noOcclusion()
                    .pushReaction(PushReaction.BLOCK)
                    .strength(0.5F, 6.0F)
            ));

    public static final RegistryObject<Block> STORAGE_STACK_BLOCK = BLOCKS.register("storage_stack_block",
            () -> new StorageStackBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.METAL)
                    .noOcclusion()
                    .pushReaction(PushReaction.BLOCK)
                    .strength(0.5F, 6.0F)
            ));

    public static final RegistryObject<BlockEntityType<BarStackBE>> BAR_STACK_BE =
            BLOCK_ENTITIES.register("bar_stack_be",
                    () -> BlockEntityType.Builder.of(BarStackBE::new, BAR_STACK_BLOCK.get()).build(null));

    public static final RegistryObject<BlockEntityType<SinglesStackBE>> SINGLES_STACK_BE =
            BLOCK_ENTITIES.register("singles_stack_be",
                    () -> BlockEntityType.Builder.of(SinglesStackBE::new, SINGLES_STACK_BLOCK.get()).build(null));

    public static final RegistryObject<BlockEntityType<StorageStackBE>> STORAGE_STACK_BE =
            BLOCK_ENTITIES.register("stack_be",
                    () -> BlockEntityType.Builder.of(StorageStackBE::new, STORAGE_STACK_BLOCK.get()).build(null));

    public static void init(IEventBus modBus) {
        BLOCKS.register(modBus);
        BLOCK_ENTITIES.register(modBus);
    }
}
