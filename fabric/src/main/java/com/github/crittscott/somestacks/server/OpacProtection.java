package com.github.crittscott.somestacks.server;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import xaero.pac.common.server.api.OpenPACServerAPI;

/**
 * Optional Open Parties and Claims integration for {@link FabricEditAuthority}, alongside
 * {@link FtbChunksProtection}. OPAC's own API documentation recommends this exact method on
 * Fabric specifically, since Fabric has no generic block-place event for {@code preparePlacement}
 * to rely on the way Forge's does. OPAC's types are referenced only inside this class, and only
 * once {@link #isLoaded()} confirms the mod is actually present, so a server without it never
 * touches them.
 */
final class OpacProtection {
    private static final boolean LOADED =
            FabricLoader.getInstance().isModLoaded("openpartiesandclaims");

    private OpacProtection() {
    }

    static boolean isLoaded() {
        return LOADED;
    }

    /** Whether OPAC refuses {@code actor} placing a block at {@code pos}. */
    static boolean prevents(ServerLevel level, ServerPlayer actor, BlockPos pos) {
        return OpenPACServerAPI.get(level.getServer()).getChunkProtection()
                .onEntityPlaceBlock(actor, level, pos);
    }
}
