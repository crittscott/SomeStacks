package com.github.crittscott.somestacks.server;

import eu.pb4.common.protection.api.CommonProtection;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

/** Common Protection API calls are confined to this class, loaded only when the mod is present. */
final class CommonProtectionQueries implements FabricProtectionQueries {
    @Override
    public boolean mayPlace(Player placer, ServerLevel level, BlockPos pos) {
        return CommonProtection.canPlaceBlock(level, pos, placer.getGameProfile(), placer);
    }

    @Override
    public boolean mayUseAdjacent(ServerPlayer player, BlockPos pos) {
        return CommonProtection.canInteractBlock(
                player.serverLevel(), pos, player.getGameProfile(), player);
    }
}
