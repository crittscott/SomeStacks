package com.github.crittscott.somestacks.block;

import com.github.crittscott.somestacks.FabricRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.transfer.v1.item.ItemStorage;
import net.minecraft.resources.Identifier;

import static com.github.crittscott.somestacks.SomeStacksCommon.MODID;

/** Registers whole-run item storages for all three Fabric block entity types. */
public final class FabricItemStorage {
    /** Each block entity's transfer adapter, kept for its lifetime; neither saved nor synced. */
    private static final AttachmentType<FabricRunItemStorage> RUN_STORAGE =
            AttachmentRegistry.create(Identifier.fromNamespaceAndPath(MODID, "run_storage"));

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

    /** The transfer adapter attached to {@code be}, created on first use. */
    static FabricRunItemStorage of(StackBlockEntity be) {
        return be.getAttachedOrCreate(RUN_STORAGE, () -> new FabricRunItemStorage(be));
    }
}
