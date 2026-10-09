package com.github.crittscott.somestacks.server;

import com.github.crittscott.somestacks.PlatformServices;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import javax.annotation.Nullable;

/**
 * The loader-specific half of stack world edits: who performs automation, and whether a claim or
 * logging mod vetoes an edit. {@link WorldEdits} owns the vanilla mechanics and actor propagation.
 *
 * <p>The loader supplies this authority as part of its {@link PlatformServices.Backend} before any
 * world logic can run.
 */
public interface EditAuthority {
    interface PlacementVeto {
        /** Whether the loader refuses the placement before the world is changed. */
        default boolean isVetoedBefore(Player placer, Direction placedAgainst) {
            return false;
        }

        /** Restores the captured block and block-entity data after a post-placement veto. */
        default void restore() {
        }

        /** Whether the loader refuses the placement after the new state exists. */
        default boolean isVetoedAfter(Player placer, Direction placedAgainst) {
            return false;
        }
    }

    /** The actor automation-driven growth and removal are attributed to. */
    ServerPlayer automationActor(ServerLevel level);

    /** Captures the loader-specific checks needed before and after a placement. */
    PlacementVeto preparePlacement(ServerLevel level, BlockPos pos);

    /** Whether a claim, protection, or logging mod refuses this removal. */
    boolean vetoesRemoval(
            ServerPlayer actor, ServerLevel level, BlockPos pos, BlockState state,
            @Nullable BlockEntity blockEntity);

    /** Reports a completed removal to loader-native observers. */
    default void afterRemoval(
            ServerPlayer actor, ServerLevel level, BlockPos pos, BlockState state,
            @Nullable BlockEntity blockEntity) {
    }
}
