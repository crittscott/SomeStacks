package com.github.crittscott.somestacks.server;

import net.fabricmc.fabric.api.entity.FakePlayer;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;

/**
 * Fabric destination block-use callbacks and native block-break lifecycle for player-attributed
 * edits. Placement relies on the vanilla checks {@link WorldEdits} performs; automated growth also
 * consults the block-use callbacks.
 */
public final class FabricEditAuthority implements EditAuthority {
    @Override
    public ServerPlayer automationActor(ServerLevel level) {
        return FakePlayer.get(level, AutomationActor.PROFILE);
    }

    @Override
    public boolean mayUseAdjacent(ServerPlayer player, BlockPos pos) {
        return UseBlockCallback.EVENT.invoker().interact(
                player, player.level(), InteractionHand.MAIN_HAND,
                WorldEdits.adjacentHit(player, pos)) == InteractionResult.PASS;
    }

    @Override
    public boolean mayPlace(Player placer, ServerLevel level, BlockPos pos) {
        // A player's own click already ran the block-use callbacks; automation has no click, so its
        // growth consults them here, where a refusal reaches simulation and commit alike.
        return placer != automationActor(level) || WorldEdits.consulting(() ->
                UseBlockCallback.EVENT.invoker().interact(
                        (ServerPlayer) placer, level, InteractionHand.MAIN_HAND,
                        new BlockHitResult(Vec3.atCenterOf(pos), Direction.DOWN, pos, false))
                        == InteractionResult.PASS);
    }

    @Override
    public boolean place(Player placer, ServerLevel level, BlockPos pos,
                         BlockState state, Direction placedAgainst) {
        return level.setBlock(pos, state, Block.UPDATE_ALL);
    }

    @Override
    public boolean vetoesRemoval(
            ServerPlayer actor, ServerLevel level, BlockPos pos, BlockState state,
            @Nullable BlockEntity blockEntity) {
        boolean allowed = PlayerBlockBreakEvents.BEFORE.invoker()
                .beforeBlockBreak(level, actor, pos, state, blockEntity);
        if (!allowed) {
            PlayerBlockBreakEvents.CANCELED.invoker()
                    .onBlockBreakCanceled(level, actor, pos, state, blockEntity);
        }
        return !allowed;
    }

    @Override
    public void afterRemoval(
            ServerPlayer actor, ServerLevel level, BlockPos pos, BlockState state,
            @Nullable BlockEntity blockEntity) {
        PlayerBlockBreakEvents.AFTER.invoker()
                .afterBlockBreak(level, actor, pos, state, blockEntity);
    }
}
