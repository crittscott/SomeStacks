package com.github.crittscott.somestacks.server;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;

/** Loader-specific protection checks for packet-driven player gestures. */
public interface PlayerEditAuthority {
    boolean claimInteraction(ServerPlayer player, BlockPos markPos, BlockPos... consulted);

    boolean claimItemUse(ServerPlayer player, BlockPos markPos, BlockPos... consulted);

    boolean claimPlacement(ServerPlayer player, BlockPos againstPos, BlockPos intoPos);

    boolean mayInteract(ServerPlayer player, BlockPos pos);
}
