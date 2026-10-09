package com.github.crittscott.somestacks.server;

import net.fabricmc.fabric.api.entity.FakePlayer;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.loader.api.FabricLoader;
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
    @Nullable
    private final FabricProtectionQueries protectionQueries = FabricLoader.getInstance()
            .isModLoaded("common-protection-api") ? new CommonProtectionQueries() : null;

    @Override
    public ServerPlayer automationActor(ServerLevel level) {
        return FakePlayer.get(level, AutomationActor.PROFILE);
    }

    @Override
    public boolean mayUseAdjacent(ServerPlayer player, BlockPos pos) {
        return protectionQueries != null && protectionQueries.mayUseAdjacent(player, pos);
    }

    @Override
    public boolean mayPlace(Player placer, ServerLevel level, BlockPos pos) {
        return protectionQueries == null || protectionQueries.mayPlace(placer, level, pos);
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
