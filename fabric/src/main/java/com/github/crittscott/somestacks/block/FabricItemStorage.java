package com.github.crittscott.somestacks.block;

import com.github.crittscott.somestacks.FabricRegistry;
import com.github.crittscott.somestacks.SomeStacksCommon;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.transfer.v1.item.ItemStorage;
import net.minecraft.resources.Identifier;

/** Registers whole-run item storages for all three Fabric block entity types. */
public final class FabricItemStorage {
    /** Each block entity's adapter for its lifetime; neither saved nor synchronized. */
    private static final AttachmentType<FabricRunItemStorage> RUN_STORAGE = AttachmentRegistry.create(
            Identifier.fromNamespaceAndPath(SomeStacksCommon.MODID, "run_storage"));

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

    /** The adapter attached to {@code blockEntity}, created on first use. */
    static FabricRunItemStorage of(StackBlockEntity blockEntity) {
        return blockEntity.getAttachedOrCreate(RUN_STORAGE, () -> new FabricRunItemStorage(blockEntity));
    }
}
