package com.github.crittscott.somestacks.block;

import com.github.crittscott.somestacks.FabricRegistry;
import com.github.crittscott.somestacks.SomeStacksCommon;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentTarget;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.transfer.v1.item.ItemStorage;
import net.minecraft.resources.ResourceLocation;

/** Registers whole-run item storages for all three Fabric block entity types. */
public final class FabricItemStorage {
    private static final AttachmentType<FabricRunItemStorage> STORAGE = AttachmentRegistry.create(
            ResourceLocation.fromNamespaceAndPath(SomeStacksCommon.MODID, "item_storage"));

    private FabricItemStorage() {
    }

    public static void init() {
        ItemStorage.SIDED.registerForBlockEntity(
                (blockEntity, direction) -> of(blockEntity),
                FabricRegistry.STORAGE_STACK_BE);
        ItemStorage.SIDED.registerForBlockEntity(
                (blockEntity, direction) -> of(blockEntity),
                FabricRegistry.SINGLES_STACK_BE);
        ItemStorage.SIDED.registerForBlockEntity(
                (blockEntity, direction) -> of(blockEntity),
                FabricRegistry.BAR_STACK_BE);
    }

    /** One transient adapter per block entity, shared by sided lookups and the run's base ledger. */
    static FabricRunItemStorage of(StackBlockEntity blockEntity) {
        return ((AttachmentTarget) blockEntity).getAttachedOrCreate(
                STORAGE, () -> new FabricRunItemStorage(blockEntity));
    }
}
