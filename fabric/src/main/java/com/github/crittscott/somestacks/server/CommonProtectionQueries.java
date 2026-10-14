package com.github.crittscott.somestacks.server;

import eu.pb4.common.protection.api.CommonProtection;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/**
 * Optional Common Protection API integration for {@link FabricEditAuthority}, alongside
 * {@link FtbChunksProtection} and {@link OpacProtection}. Every claim mod that implements the API
 * answers through it. Its types are referenced only inside this class, and only once
 * {@link #isLoaded()} confirms the mod is actually present, so a server without it never touches
 * them.
 */
final class CommonProtectionQueries {
    private static final boolean LOADED =
            FabricLoader.getInstance().isModLoaded("common-protection-api");

    private CommonProtectionQueries() {
    }

    static boolean isLoaded() {
        return LOADED;
    }

    /** Whether the installed providers let {@code actor} place a block at {@code pos}. */
    static boolean mayPlace(ServerPlayer actor, ServerLevel level, BlockPos pos) {
        return CommonProtection.canPlaceBlock(level, pos, actor.getGameProfile(), actor);
    }

    /** Whether the installed providers let {@code player} use the block at {@code pos}. */
    static boolean mayInteract(ServerPlayer player, BlockPos pos) {
        return CommonProtection.canInteractBlock(
                player.serverLevel(), pos, player.getGameProfile(), player);
    }
}
