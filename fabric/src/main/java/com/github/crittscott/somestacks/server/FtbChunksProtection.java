package com.github.crittscott.somestacks.server;

import dev.ftb.mods.ftbchunks.api.FTBChunksAPI;
import dev.ftb.mods.ftbchunks.api.Protection;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;

/**
 * Optional FTB Chunks integration for {@link FabricEditAuthority}. Fabric API has no generic
 * block-place event the way Forge's does, so automated growth has nothing to fire on this
 * platform; this is the one claim mod SomeStacks consults directly to close that gap. FTB
 * Chunks' types are referenced only inside this class, and only once {@link #isLoaded()} confirms
 * the mod is actually present, so a server without it never touches them.
 */
final class FtbChunksProtection {
    private static final boolean LOADED = FabricLoader.getInstance().isModLoaded("ftbchunks");

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
