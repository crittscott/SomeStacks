package com.github.crittscott.somestacks.server;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.TriState;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.BlockSnapshot;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.event.EventHooks;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockEvent;

import java.util.ArrayList;

import javax.annotation.Nullable;

/**
 * NeoForge's {@link EditAuthority}: the level's fake player stands in for automation, and claim,
 * protection, and logging mods are given the chance to veto through the ordinary NeoForge placement
 * and break events.
 */
public final class NeoForgeEditAuthority implements EditAuthority {
    @Override
    public boolean mayUseAdjacent(ServerPlayer player, BlockPos pos) {
        var event =
                new PlayerInteractEvent.RightClickBlock(
                        player,
                        InteractionHand.MAIN_HAND,
                        pos,
                        WorldEdits.adjacentHit(player, pos));
        NeoForge.EVENT_BUS.post(event);
        return !event.isCanceled()
                && event.getUseBlock() != TriState.FALSE
                && event.getUseItem() != TriState.FALSE;
    }

    @Override
    public ServerPlayer automationActor(ServerLevel level) {
        return FakePlayerFactory.get(level, AutomationActor.PROFILE);
    }

    private boolean placing;

    /**
     * Places under NeoForge's snapshot capture, the way NeoForge's own item placement does, rather
     * than with the shorter snapshot, {@code setBlock}, place event, restore-on-veto pattern.
     * Outside capture, {@code setBlock} runs the new block's {@code onPlace} and notifies neighbors
     * before the event can veto, so neighbors would react to a block that is then rolled back.
     * Under capture those side effects are held back and published only after the event allows
     * the placement.
     */
    @Override
    public boolean place(
            ServerPlayer placer,
            ServerLevel level,
            BlockPos pos,
            BlockState state,
            Direction placedAgainst) {
        if (placing || level.captureBlockSnapshots || level.restoringBlockSnapshots) {
            return false;
        }
        placing = true;
        var snapshots = new ArrayList<BlockSnapshot>();
        try {
            if (!NeoForgeGrowthPlacement.capture(level, pos, state, snapshots)
                    || snapshots.isEmpty()
                    || EventHooks.onBlockPlace(placer, snapshots.get(0), placedAgainst)) {
                NeoForgeGrowthPlacement.restore(level, snapshots);
                return false;
            }
            NeoForgeGrowthPlacement.publishSnapshots(level, snapshots);
            return true;
        } finally {
            placing = false;
        }
    }

    @Override
    public boolean vetoesRemoval(
            ServerPlayer actor,
            ServerLevel level,
            BlockPos pos,
            BlockState state,
            @Nullable BlockEntity blockEntity) {
        BlockEvent.BreakEvent evt = new BlockEvent.BreakEvent(level, pos, state, actor);
        return NeoForge.EVENT_BUS.post(evt).isCanceled();
    }
}
