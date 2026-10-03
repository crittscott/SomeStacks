package com.github.crittscott.somestacks.server;

import eu.pb4.common.protection.api.CommonProtection;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/**
 * Optional Common Protection API integration for {@link FabricEditAuthority}. Fabric API has no
 * generic block-place event the way Forge's does, so automated growth has nothing to fire on this
 * platform; claim mods that implement Common Protection API are asked directly to close that gap.
 * The API's types are referenced only inside this class, and only once {@link #isLoaded()}
 * confirms it is present, so a server without it never touches them.
 */
final class CommonProtectionCheck {
    private static final boolean LOADED = FabricLoader.getInstance().isModLoaded("common-protection-api");

    private CommonProtectionCheck() {
    }

    static boolean isLoaded() {
        return LOADED;
    }

    /** Whether a registered protection provider refuses {@code actor} placing a block at {@code pos}. */
    static boolean prevents(ServerLevel level, ServerPlayer actor, BlockPos pos) {
        return !CommonProtection.canPlaceBlock(level, pos, actor.getGameProfile(), actor);
    }
}
