package com.github.crittscott.somestacks.server;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;

/** Preserves Forge interaction-event and trailing-click suppression behavior. */
public final class ForgePlayerEditAuthority implements PlayerEditAuthority {
    @Override
    public boolean claimInteraction(
            ServerPlayer player, BlockPos markPos, BlockPos... consulted) {
        return Protection.claimInteraction(player, markPos, consulted);
    }

    @Override
    public boolean claimItemUse(
            ServerPlayer player, BlockPos markPos, BlockPos... consulted) {
        return Protection.claimItemUse(player, markPos, consulted);
    }

    @Override
    public boolean claimPlacement(
            ServerPlayer player, BlockPos againstPos, BlockPos intoPos) {
        return Protection.claimPlacement(player, againstPos, intoPos);
    }

    @Override
    public boolean mayInteract(ServerPlayer player, BlockPos pos) {
        return Protection.mayInteract(player, pos);
    }
}
