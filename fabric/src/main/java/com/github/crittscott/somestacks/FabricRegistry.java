package com.github.crittscott.somestacks;

import com.github.crittscott.somestacks.block.BarStackBE;
import com.github.crittscott.somestacks.block.BarStackBlock;
import com.github.crittscott.somestacks.block.FabricRunItemStorage;
import com.github.crittscott.somestacks.block.SinglesStackBE;
import com.github.crittscott.somestacks.block.SinglesStackBlock;
import com.github.crittscott.somestacks.block.StackBlock;
import com.github.crittscott.somestacks.block.StackBlockEntity;
import com.github.crittscott.somestacks.block.StorageStackBE;
import com.github.crittscott.somestacks.block.StorageStackBlock;

import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentTarget;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.object.builder.v1.block.entity.FabricBlockEntityTypeBuilder;
import net.fabricmc.fabric.api.transfer.v1.item.ItemStorage;
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

/**
 * Fabric registration for the shared blocks, block entities, sounds, and the block entities'
 * whole-run Transfer API item storage.
 */
public final class FabricRegistry {
    private FabricRegistry() {}

    /**
     * Each stack block entity's Transfer API adapter. Transfer callers on every side of a block,
     * and every block of a run resolving its base's ledger, must share one {@link
     * FabricRunItemStorage} per entity, because it is the {@code SnapshotParticipant} that holds
     * the transaction's staged edits. Common code cannot declare a field of that Fabric-only type,
     * so the adapter is a transient (unsaved) Data Attachment, whose lifetime is the entity's.
     */
    private static final AttachmentType<FabricRunItemStorage> ITEM_STORAGE =
            AttachmentRegistry.create(
                    ResourceLocation.fromNamespaceAndPath(SomeStacksCommon.MODID, "item_storage"));

    public static final Block BAR_STACK_BLOCK =
            registerBlock(
                    CommonRegistry.BAR_STACK_BLOCK, BarStackBlock::new, StackBlock.barProperties());

    public static final Block SINGLES_STACK_BLOCK =
            registerBlock(
                    CommonRegistry.SINGLES_STACK_BLOCK,
                    SinglesStackBlock::new,
                    StackBlock.singlesProperties());

    public static final Block STORAGE_STACK_BLOCK =
            registerBlock(
                    CommonRegistry.STORAGE_STACK_BLOCK,
                    StorageStackBlock::new,
                    StackBlock.storageProperties());

    public static final BlockEntityType<BarStackBE> BAR_STACK_BE =
            registerBlockEntity(
                    CommonRegistry.BAR_STACK_BE,
                    FabricBlockEntityTypeBuilder.create(BarStackBE::new, BAR_STACK_BLOCK).build());

    public static final BlockEntityType<SinglesStackBE> SINGLES_STACK_BE =
            registerBlockEntity(
                    CommonRegistry.SINGLES_STACK_BE,
                    FabricBlockEntityTypeBuilder.create(SinglesStackBE::new, SINGLES_STACK_BLOCK)
                            .build());

    public static final BlockEntityType<StorageStackBE> STORAGE_STACK_BE =
            registerBlockEntity(
                    CommonRegistry.STORAGE_STACK_BE,
                    FabricBlockEntityTypeBuilder.create(StorageStackBE::new, STORAGE_STACK_BLOCK)
                            .build());

    /**
     * Initializes this class, which registers the blocks and block entity types, then registers
     * every sound and each block entity type's whole-run item storage for every side. Runs once
     * from the common entry point.
     */
    public static void init() {
        for (CommonRegistry.Sound sound : CommonRegistry.Sound.values()) {
            Registry.register(
                    BuiltInRegistries.SOUND_EVENT,
                    sound.id(),
                    SoundEvent.createVariableRangeEvent(sound.id()));
        }
        ItemStorage.SIDED.registerForBlockEntity(
                (blockEntity, direction) -> itemStorage(blockEntity), STORAGE_STACK_BE);
        ItemStorage.SIDED.registerForBlockEntity(
                (blockEntity, direction) -> itemStorage(blockEntity), SINGLES_STACK_BE);
        ItemStorage.SIDED.registerForBlockEntity(
                (blockEntity, direction) -> itemStorage(blockEntity), BAR_STACK_BE);
    }

    /**
     * The block entity's one Transfer API adapter, created on first use. A whole-run view:
     * whichever block a caller asks, it reads and stages edits against the complete run.
     */
    public static FabricRunItemStorage itemStorage(StackBlockEntity blockEntity) {
        return ((AttachmentTarget) blockEntity)
                .getAttachedOrCreate(ITEM_STORAGE, () -> new FabricRunItemStorage(blockEntity));
    }

    private static Block registerBlock(
            ResourceLocation id,
            Function<BlockBehaviour.Properties, Block> factory,
            BlockBehaviour.Properties properties) {
        ResourceKey<Block> key = ResourceKey.create(Registries.BLOCK, id);
        return Registry.register(
                BuiltInRegistries.BLOCK, key, factory.apply(properties.setId(key)));
    }

    private static <T extends BlockEntityType<?>> T registerBlockEntity(
            ResourceLocation id, T type) {
        return Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE, id, type);
    }
}
