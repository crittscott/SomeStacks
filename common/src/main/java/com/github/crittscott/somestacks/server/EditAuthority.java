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
    @FunctionalInterface
    interface PlacementVeto {
        boolean isVetoed(Player placer, Direction placedAgainst);
    }

    /** The actor automation-driven growth and removal are attributed to. */
    ServerPlayer automationActor(ServerLevel level);

    /** Captures the loader-specific state needed to evaluate a placement after it occurs. */
    PlacementVeto preparePlacement(ServerLevel level, BlockPos pos);

    /** Whether a claim, protection, or logging mod refuses this removal. */
    boolean vetoesRemoval(ServerLevel level, BlockPos pos, BlockState state);
}
