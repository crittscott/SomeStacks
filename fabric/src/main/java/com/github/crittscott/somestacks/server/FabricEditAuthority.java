package com.github.crittscott.somestacks.server;

import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.Map;

/** Fabric protection queries and native block-break lifecycle for player-attributed edits. */
public final class FabricEditAuthority implements EditAuthority {
    private final Map<ResourceKey<Level>, ServerPlayer> actors = new HashMap<>();
    @Nullable
    private final FabricProtectionQueries protectionQueries = FabricLoader.getInstance()
            .isModLoaded("common-protection-api") ? new CommonProtectionQueries() : null;

    @Override
    public ServerPlayer automationActor(ServerLevel level) {
        return actors.computeIfAbsent(level.dimension(), key ->
                new SyntheticPlayer(level, AutomationActor.PROFILE));
    }

    /** Releases actors retaining worlds after a server stops. */
    public void clear() { actors.clear(); }

    @Override
    public boolean mayUseAdjacent(ServerPlayer player, BlockPos pos) {
        if (protectionQueries != null && !protectionQueries.mayUseAdjacent(player, pos)) {
            return false;
        }
        return UseBlockCallback.EVENT.invoker().interact(
                player, player.serverLevel(), InteractionHand.MAIN_HAND,
                WorldEdits.adjacentHit(player, pos)) == InteractionResult.PASS;
    }

    @Override
    public boolean mayPlace(Player placer, ServerLevel level, BlockPos pos) {
        return (protectionQueries == null || protectionQueries.mayPlace(placer, level, pos))
                && !(placer instanceof ServerPlayer actor
                && FtbChunksProtection.isLoaded() && FtbChunksProtection.prevents(actor, pos));
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
        boolean allowed = (protectionQueries == null || protectionQueries.mayBreak(actor, level, pos))
                && PlayerBlockBreakEvents.BEFORE.invoker()
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
