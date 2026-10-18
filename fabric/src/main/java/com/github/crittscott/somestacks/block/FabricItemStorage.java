package com.github.crittscott.somestacks.block;

import com.github.crittscott.somestacks.FabricRegistry;
import com.github.crittscott.somestacks.SomeStacksCommon;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.transfer.v1.item.ItemStorage;
import net.minecraft.resources.ResourceLocation;

/** Registers whole-run item storages for all three Fabric block entity types. */
public final class FabricItemStorage {
    /** Each block entity's lazily created Transfer API adapter; neither saved nor synced. */
    private static final AttachmentType<FabricRunItemStorage> RUN_STORAGE = AttachmentRegistry.create(
            ResourceLocation.fromNamespaceAndPath(SomeStacksCommon.MODID, "run_storage"));

    private FabricItemStorage() {
    }

    public static void init() {
        ItemStorage.SIDED.registerForBlockEntity(
                (blockEntity, direction) -> of(blockEntity), FabricRegistry.STORAGE_STACK_BE);
        ItemStorage.SIDED.registerForBlockEntity(
                (blockEntity, direction) -> of(blockEntity), FabricRegistry.SINGLES_STACK_BE);
        ItemStorage.SIDED.registerForBlockEntity(
                (blockEntity, direction) -> of(blockEntity), FabricRegistry.BAR_STACK_BE);
    }

    /** The adapter that stays with {@code blockEntity} for its lifetime. */
    static FabricRunItemStorage of(StackBlockEntity blockEntity) {
        return blockEntity.getAttachedOrCreate(RUN_STORAGE, () -> new FabricRunItemStorage(blockEntity));
    }
}
