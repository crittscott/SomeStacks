package com.github.crittscott.somestacks.server;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.BlockSnapshot;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.event.EventHooks;
import net.neoforged.neoforge.event.level.BlockEvent;

import javax.annotation.Nullable;

/**
 * NeoForge's {@link EditAuthority}: the level's fake player stands in for automation, and claim,
 * protection, and logging mods are given the chance to veto through the ordinary NeoForge placement
 * and break events.
 */
public final class NeoForgeEditAuthority implements EditAuthority {
    @Override
    public ServerPlayer automationActor(ServerLevel level) {
        return FakePlayerFactory.get(level, AutomationActor.PROFILE);
    }

    @Override
    public EditAuthority.PlacementVeto preparePlacement(ServerLevel level, BlockPos pos) {
        BlockSnapshot snapshot = BlockSnapshot.create(level.dimension(), level, pos);
        return (placer, placedAgainst) -> EventHooks.onBlockPlace(placer, snapshot, placedAgainst);
    }

    @Override
    public boolean vetoesRemoval(
            ServerLevel level, BlockPos pos, BlockState state, @Nullable BlockEntity blockEntity) {
        ServerPlayer breaker = automationActor(level);
        BlockEvent.BreakEvent evt = new BlockEvent.BreakEvent(level, pos, state, breaker);
        return NeoForge.EVENT_BUS.post(evt).isCanceled();
    }
}
