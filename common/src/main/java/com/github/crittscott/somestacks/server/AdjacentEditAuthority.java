package com.github.crittscott.somestacks.server;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;

/** Loader-native permission consultation for a stack reached through its neighboring block. */
public interface AdjacentEditAuthority {
    boolean mayUseItemAt(ServerPlayer player, BlockPos pos);
}
