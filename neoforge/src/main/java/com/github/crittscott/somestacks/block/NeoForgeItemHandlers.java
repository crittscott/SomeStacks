package com.github.crittscott.somestacks.block;

import com.github.crittscott.somestacks.NeoForgeRegistry;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;

/**
 * Exposes {@code Capabilities.Item.BLOCK} on all stack types. The transient attachment retains
 * one transaction journal per block entity; every view delegates to the run base's journal.
 */
public final class NeoForgeItemHandlers {
    private NeoForgeItemHandlers() {}

    public static void onRegisterCapabilities(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(
                Capabilities.Item.BLOCK, NeoForgeRegistry.STORAGE_STACK_BE.get(),
                (blockEntity, side) -> blockEntity.getData(NeoForgeRegistry.RUN_RESOURCES));
        event.registerBlockEntity(
                Capabilities.Item.BLOCK, NeoForgeRegistry.SINGLES_STACK_BE.get(),
                (blockEntity, side) -> blockEntity.getData(NeoForgeRegistry.RUN_RESOURCES));
        event.registerBlockEntity(
                Capabilities.Item.BLOCK, NeoForgeRegistry.BAR_STACK_BE.get(),
                (blockEntity, side) -> blockEntity.getData(NeoForgeRegistry.RUN_RESOURCES));
    }
}
