package com.github.crittscott.somestacks.server;

import eu.pb4.common.protection.api.CommonProtection;
import net.fabricmc.fabric.api.entity.FakePlayer;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import javax.annotation.Nullable;

/** Fabric protection queries and native block-break lifecycle for player-attributed edits. */
public final class FabricEditAuthority implements EditAuthority {
    @Override
    public ServerPlayer automationActor(ServerLevel level) {
        return FakePlayer.get(level, AutomationActor.PROFILE);
    }

    @Override
    public boolean mayUseAdjacent(ServerPlayer player, BlockPos pos) {
        return CommonProtection.canInteractBlock(
                player.serverLevel(), pos, player.getGameProfile(), player);
    }

    @Override
    public boolean mayPlace(Player placer, ServerLevel level, BlockPos pos) {
        return CommonProtection.canPlaceBlock(level, pos, placer.getGameProfile(), placer);
    }

    @Override
    public boolean place(Player placer, ServerLevel level, BlockPos pos,
                         BlockState state, Direction placedAgainst) {
        return mayPlace(placer, level, pos)
                && level.setBlock(pos, state, Block.UPDATE_ALL);
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
