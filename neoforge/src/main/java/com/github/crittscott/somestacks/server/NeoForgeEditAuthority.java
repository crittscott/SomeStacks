package com.github.crittscott.somestacks.server;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.BlockSnapshot;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.event.EventHooks;
import net.neoforged.neoforge.event.level.BlockEvent;

/**
 * NeoForge's {@link EditAuthority}: the level's fake player stands in for automation, and claim,
 * protection, and logging mods are given the chance to veto through the ordinary NeoForge placement
 * and break events. FTB Chunks is additionally consulted directly for placement when installed.
 */
public final class NeoForgeEditAuthority implements EditAuthority {
    @Override
    public ServerPlayer automationActor(ServerLevel level) {
        return FakePlayerFactory.getMinecraft(level);
    }

    @Override
    public EditAuthority.PlacementVeto preparePlacement(ServerLevel level, BlockPos pos) {
        BlockSnapshot snapshot = BlockSnapshot.create(level.dimension(), level, pos);
        return (placer, placedAgainst) -> {
            if (EventHooks.onBlockPlace(placer, snapshot, placedAgainst)) {
                return true;
            }
            return FtbChunksProtection.isLoaded()
                    && placer instanceof ServerPlayer actor
                    && FtbChunksProtection.prevents(actor, pos);
        };
    }

    @Override
    public boolean vetoesRemoval(ServerLevel level, BlockPos pos, BlockState state) {
        ServerPlayer breaker = FakePlayerFactory.getMinecraft(level);
        BlockEvent.BreakEvent evt = new BlockEvent.BreakEvent(level, pos, state, breaker);
        return NeoForge.EVENT_BUS.post(evt).isCanceled();
    }
}
