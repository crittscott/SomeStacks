package com.github.crittscott.somestacks.server;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The loader-specific half of automated world edits: who performs them, and whether a claim or
 * logging mod vetoes one. {@link WorldEdits} owns the vanilla mechanics and consults this for both.
 *
 * <p>The loader's entry point installs an implementation via {@link WorldEdits#setAuthority} before
 * any world logic can run.
 */
public interface EditAuthority {
    /** The actor automation-driven growth and removal are attributed to. */
    ServerPlayer automationActor(ServerLevel level);

    /** Places through loader-native protection, restoring denied state before publication. */
    boolean place(Player placer, ServerLevel level, BlockPos pos,
                  BlockState state, Direction placedAgainst);

    /** Whether a neighboring click may deposit into an existing destination stack. */
    boolean mayUseAdjacent(ServerPlayer player, BlockPos pos);

    /** Whether a claim, protection, or logging mod refuses this removal. */
    boolean vetoesRemoval(ServerLevel level, BlockPos pos, BlockState state);
}
