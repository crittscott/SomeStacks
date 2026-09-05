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
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;

/** Fabric registration for the shared blocks and block entities. */
public final class FabricRegistry {
    private FabricRegistry() {}

    public static final Block BAR_STACK_BLOCK = registerBlock("bar_stack_block",
            new BarStackBlock(BlockBehaviour.Properties.of()
                    .dynamicShape()
                    .mapColor(MapColor.METAL)
                    .noOcclusion()
                    .pushReaction(PushReaction.BLOCK)
                    .strength(0.5F, 6.0F)
                    .lightLevel(state -> state.getValue(BarStackBlock.LIGHT_LEVEL))));

    public static final Block SINGLES_STACK_BLOCK = registerBlock("singles_stack_block",
            new SinglesStackBlock(BlockBehaviour.Properties.of()
                    .dynamicShape()
                    .mapColor(MapColor.WOOD)
                    .noOcclusion()
                    .pushReaction(PushReaction.BLOCK)
                    .strength(0.5F, 6.0F)
                    .lightLevel(state -> state.getValue(SinglesStackBlock.LIGHT_LEVEL))));

    public static final Block STORAGE_STACK_BLOCK = registerBlock("storage_stack_block",
            new StorageStackBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.METAL)
                    .noOcclusion()
                    .pushReaction(PushReaction.BLOCK)
                    .strength(0.5F, 6.0F)
                    .lightLevel(state -> state.getValue(StorageStackBlock.LIGHT_LEVEL))));

    public static final BlockEntityType<BarStackBE> BAR_STACK_BE = registerBlockEntity(
            "bar_stack_be", FabricBlockEntityTypeBuilder.create(BarStackBE::new, BAR_STACK_BLOCK).build());

    public static final BlockEntityType<SinglesStackBE> SINGLES_STACK_BE = registerBlockEntity(
            "singles_stack_be",
            FabricBlockEntityTypeBuilder.create(SinglesStackBE::new, SINGLES_STACK_BLOCK).build());

    public static final BlockEntityType<StorageStackBE> STORAGE_STACK_BE = registerBlockEntity(
            "stack_be",
            FabricBlockEntityTypeBuilder.create(StorageStackBE::new, STORAGE_STACK_BLOCK).build());

    public static void init() {
        CommonRegistry.STORAGE_STACK_BLOCK = () -> STORAGE_STACK_BLOCK;
        CommonRegistry.SINGLES_STACK_BLOCK = () -> SINGLES_STACK_BLOCK;
        CommonRegistry.BAR_STACK_BLOCK = () -> BAR_STACK_BLOCK;
        CommonRegistry.STORAGE_STACK_BE = () -> STORAGE_STACK_BE;
        CommonRegistry.SINGLES_STACK_BE = () -> SINGLES_STACK_BE;
        CommonRegistry.BAR_STACK_BE = () -> BAR_STACK_BE;
    }

    private static Block registerBlock(String path, Block block) {
        return Registry.register(BuiltInRegistries.BLOCK, id(path), block);
    }

    private static <T extends BlockEntityType<?>> T registerBlockEntity(String path, T type) {
        return Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE, id(path), type);
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(SomeStacksCommon.MODID, path);
    }
}
