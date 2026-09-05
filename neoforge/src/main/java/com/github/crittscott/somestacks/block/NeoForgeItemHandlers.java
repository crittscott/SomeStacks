package com.github.crittscott.somestacks.block;

import com.github.crittscott.somestacks.ModRegistry;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;

/**
 * Exposes {@code Capabilities.ItemHandler.BLOCK} on the mod's three block entity types. Each
 * provider hands back a whole-run view ({@link PileItemHandler}, {@link SinglesColumnHandler},
 * {@link BarColumnHandler}) so a machine on any block in a run sees the entire run. The handler
 * instances read their run live on every call, so a run growing or shrinking does not stale them.
 */
public final class NeoForgeItemHandlers {
    private NeoForgeItemHandlers() {}

    public static void onRegisterCapabilities(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(
                Capabilities.ItemHandler.BLOCK, ModRegistry.STORAGE_STACK_BE.get(),
                (blockEntity, side) -> new PileItemHandler(blockEntity));
        event.registerBlockEntity(
                Capabilities.ItemHandler.BLOCK, ModRegistry.SINGLES_STACK_BE.get(),
                (blockEntity, side) -> new SinglesColumnHandler(blockEntity));
        event.registerBlockEntity(
                Capabilities.ItemHandler.BLOCK, ModRegistry.BAR_STACK_BE.get(),
                (blockEntity, side) -> new BarColumnHandler(blockEntity));
    }
}
