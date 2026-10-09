package com.github.crittscott.somestacks.server;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

/** Optional destination queries used when a protection provider is installed. */
interface FabricProtectionQueries {
    boolean mayPlace(Player placer, ServerLevel level, BlockPos pos);

    boolean mayUseAdjacent(ServerPlayer player, BlockPos pos);
}
