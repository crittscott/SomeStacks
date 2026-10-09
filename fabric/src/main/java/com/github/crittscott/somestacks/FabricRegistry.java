package com.github.crittscott.somestacks;

import com.github.crittscott.somestacks.block.BarStackBE;
import com.github.crittscott.somestacks.block.BarStackBlock;
import com.github.crittscott.somestacks.block.SinglesStackBE;
import com.github.crittscott.somestacks.block.SinglesStackBlock;
import com.github.crittscott.somestacks.block.StackBlock;
import com.github.crittscott.somestacks.block.StorageStackBE;
import com.github.crittscott.somestacks.block.StorageStackBlock;
import net.fabricmc.fabric.api.object.builder.v1.block.entity.FabricBlockEntityTypeBuilder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;

import java.util.function.Function;

/** Fabric registration for the shared blocks and block entities. */
public final class FabricRegistry {
    private FabricRegistry() {}

    public static final Block BAR_STACK_BLOCK = registerBlock(CommonRegistry.BAR_STACK_BLOCK, BarStackBlock::new,
            StackBlock.barProperties());

    public static final Block SINGLES_STACK_BLOCK = registerBlock(CommonRegistry.SINGLES_STACK_BLOCK, SinglesStackBlock::new,
            StackBlock.singlesProperties());

    public static final Block STORAGE_STACK_BLOCK = registerBlock(CommonRegistry.STORAGE_STACK_BLOCK, StorageStackBlock::new,
            StackBlock.storageProperties());

    public static final SoundEvent STORAGE_DEPOSIT_SOUND = registerSound(CommonRegistry.STORAGE_DEPOSIT_SOUND);
    public static final SoundEvent STORAGE_EXTRACT_SOUND = registerSound(CommonRegistry.STORAGE_EXTRACT_SOUND);
    public static final SoundEvent STORAGE_ROTATE_SOUND = registerSound(CommonRegistry.STORAGE_ROTATE_SOUND);
    public static final SoundEvent SINGLES_DEPOSIT_SOUND = registerSound(CommonRegistry.SINGLES_DEPOSIT_SOUND);
    public static final SoundEvent SINGLES_EXTRACT_SOUND = registerSound(CommonRegistry.SINGLES_EXTRACT_SOUND);
    public static final SoundEvent SINGLES_ROTATE_SOUND = registerSound(CommonRegistry.SINGLES_ROTATE_SOUND);
    public static final SoundEvent SINGLES_ROTATE_ITEM_SOUND = registerSound(CommonRegistry.SINGLES_ROTATE_ITEM_SOUND);
    public static final SoundEvent BAR_DEPOSIT_SOUND = registerSound(CommonRegistry.BAR_DEPOSIT_SOUND);
    public static final SoundEvent BAR_EXTRACT_SOUND = registerSound(CommonRegistry.BAR_EXTRACT_SOUND);

    public static final BlockEntityType<BarStackBE> BAR_STACK_BE = registerBlockEntity(
            CommonRegistry.BAR_STACK_BE,
            FabricBlockEntityTypeBuilder.create(BarStackBE::new, BAR_STACK_BLOCK).build());

    public static final BlockEntityType<SinglesStackBE> SINGLES_STACK_BE = registerBlockEntity(
            CommonRegistry.SINGLES_STACK_BE,
            FabricBlockEntityTypeBuilder.create(
                    SinglesStackBE::new, SINGLES_STACK_BLOCK).build());

    public static final BlockEntityType<StorageStackBE> STORAGE_STACK_BE = registerBlockEntity(
            CommonRegistry.STORAGE_STACK_BE,
            FabricBlockEntityTypeBuilder.create(
                    StorageStackBE::new, STORAGE_STACK_BLOCK).build());

    public static void init() {
        // Calling this method initializes the class and registers its values.
    }

    private static Block registerBlock(ResourceLocation id, Function<BlockBehaviour.Properties, Block> factory,
                                       BlockBehaviour.Properties properties) {
        ResourceKey<Block> key = ResourceKey.create(Registries.BLOCK, id);
        return Registry.register(BuiltInRegistries.BLOCK, key, factory.apply(properties.setId(key)));
    }

    private static <T extends BlockEntityType<?>> T registerBlockEntity(ResourceLocation id, T type) {
        return Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE, id, type);
    }

    private static SoundEvent registerSound(ResourceLocation id) {
        return Registry.register(BuiltInRegistries.SOUND_EVENT, id,
                SoundEvent.createVariableRangeEvent(id));
    }
}
