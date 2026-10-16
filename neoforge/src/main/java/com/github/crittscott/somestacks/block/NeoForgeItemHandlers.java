package com.github.crittscott.somestacks.block;

import com.github.crittscott.somestacks.ModRegistry;
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
                Capabilities.Item.BLOCK, ModRegistry.STORAGE_STACK_BE.get(),
                (blockEntity, side) -> blockEntity.getData(ModRegistry.RUN_RESOURCES));
        event.registerBlockEntity(
                Capabilities.Item.BLOCK, ModRegistry.SINGLES_STACK_BE.get(),
                (blockEntity, side) -> blockEntity.getData(ModRegistry.RUN_RESOURCES));
        event.registerBlockEntity(
                Capabilities.Item.BLOCK, ModRegistry.BAR_STACK_BE.get(),
                (blockEntity, side) -> blockEntity.getData(ModRegistry.RUN_RESOURCES));
    }
}
