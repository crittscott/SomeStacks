package com.github.crittscott.somestacks.block;

import com.github.crittscott.somestacks.FabricRegistry;
import net.fabricmc.fabric.api.transfer.v1.item.ItemStorage;

/** Registers whole-run item storages for all three Fabric block entity types. */
public final class FabricItemStorage {
    private FabricItemStorage() {
    }

    public static void init() {
        ItemStorage.SIDED.registerForBlockEntity(
                (blockEntity, direction) -> new FabricRunItemStorage(blockEntity),
                FabricRegistry.STORAGE_STACK_BE);
        ItemStorage.SIDED.registerForBlockEntity(
                (blockEntity, direction) -> new FabricRunItemStorage(blockEntity),
                FabricRegistry.SINGLES_STACK_BE);
        ItemStorage.SIDED.registerForBlockEntity(
                (blockEntity, direction) -> new FabricRunItemStorage(blockEntity),
                FabricRegistry.BAR_STACK_BE);
    }
}
