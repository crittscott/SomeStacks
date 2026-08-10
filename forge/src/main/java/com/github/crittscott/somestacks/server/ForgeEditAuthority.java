package com.github.crittscott.somestacks.server;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.util.BlockSnapshot;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.event.ForgeEventFactory;
import net.minecraftforge.event.level.BlockEvent;

/**
 * Forge's {@link EditAuthority}: the level's fake player stands in for automation, and claim,
 * protection, and logging mods are given the chance to veto through the ordinary Forge placement
 * and break events.
 */
public final class ForgeEditAuthority implements EditAuthority {
    @Override
    public ServerPlayer automationActor(ServerLevel level) {
        return FakePlayerFactory.getMinecraft(level);
    }

    @Override
    public EditAuthority.PlacementVeto preparePlacement(ServerLevel level, BlockPos pos) {
        BlockSnapshot snapshot = BlockSnapshot.create(level.dimension(), level, pos);
        return (placer, placedAgainst) -> ForgeEventFactory.onBlockPlace(placer, snapshot, placedAgainst);
    }

    @Override
    public boolean vetoesRemoval(ServerLevel level, BlockPos pos, BlockState state) {
        ServerPlayer breaker = FakePlayerFactory.getMinecraft(level);
        BlockEvent.BreakEvent evt = new BlockEvent.BreakEvent(level, pos, state, breaker);
        return MinecraftForge.EVENT_BUS.post(evt);
    }
}
