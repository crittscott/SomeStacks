package com.github.crittscott.somestacks.server;

import dev.ftb.mods.ftbchunks.api.FTBChunksAPI;
import dev.ftb.mods.ftbchunks.api.Protection;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.neoforged.fml.ModList;

/**
 * Optional FTB Chunks integration for {@link NeoForgeEditAuthority}. NeoForge's block-place event
 * covers claim mods that hook it; FTB Chunks is additionally consulted directly here so its claims
 * block automated growth even where it does not. FTB Chunks' types are referenced only inside this
 * class, and only once {@link #isLoaded()} confirms the mod is present.
 */
final class FtbChunksProtection {
    private static final boolean LOADED = ModList.get().isLoaded("ftbchunks");

    private FtbChunksProtection() {
    }

    static boolean isLoaded() {
        return LOADED;
    }

    /** Whether FTB Chunks refuses {@code actor} editing a block at {@code pos}. */
    static boolean prevents(ServerPlayer actor, BlockPos pos) {
        return FTBChunksAPI.api().getManager().shouldPreventInteraction(
                actor, InteractionHand.MAIN_HAND, pos, Protection.EDIT_BLOCK, null);
    }
}
