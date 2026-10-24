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
    /** Side-effect-free placement permission, where the loader provides a query API. */
    default boolean mayPlace(Player placer, ServerLevel level, BlockPos pos) {
        return true;
    }

    /** Places a block through the loader's native protection transaction. */
    boolean place(Player placer, ServerLevel level, BlockPos pos,
                  BlockState state, Direction placedAgainst);

    /** Whether a neighboring click may edit an existing destination stack without another click. */
    default boolean mayUseAdjacent(ServerPlayer player, BlockPos pos) {
        return false;
    }

    /** The actor automation-driven growth and removal are attributed to. */
    ServerPlayer automationActor(ServerLevel level);

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
