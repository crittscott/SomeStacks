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
 * The loader-specific half of automated world edits: who performs them, and whether a claim or
 * logging mod vetoes one, and how the loader reports completion. {@link WorldEdits} owns the vanilla
 * mechanics and consults this boundary for protection and notifications.
 *
 * <p>The loader's entry point supplies this authority through {@link PlatformServices.Backend}
 * before any world logic can run.
 */
public interface EditAuthority {
    /** The actor automation-driven growth and removal are attributed to. */
    ServerPlayer automationActor(ServerLevel level);

    /**
     * Side-effect-free placement permission, where the loader offers protection queries. Growth
     * consults it before promising space, and {@link #place} answers to it as well.
     */
    default boolean mayPlace(Player placer, ServerLevel level, BlockPos pos) {
        return true;
    }

    /** Places through loader-native protection, restoring denied state before publication. */
    boolean place(Player placer, ServerLevel level, BlockPos pos,
                  BlockState state, Direction placedAgainst);

    /** Whether a neighboring click may deposit into an existing destination stack. */
    boolean mayUseAdjacent(ServerPlayer player, BlockPos pos);

    /** Whether a claim, protection, or logging mod refuses this removal. */
    boolean vetoesRemoval(ServerLevel level, ServerPlayer actor, BlockPos pos,
                          BlockState state, @Nullable BlockEntity blockEntity);

    /** Reports a successful removal with the actor, state, and entity captured before the edit. */
    default void afterRemoval(ServerLevel level, ServerPlayer actor, BlockPos pos,
                              BlockState state, @Nullable BlockEntity blockEntity) {}
}
