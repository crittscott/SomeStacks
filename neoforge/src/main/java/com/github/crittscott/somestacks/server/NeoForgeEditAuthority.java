package com.github.crittscott.somestacks.server;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.BlockSnapshot;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.event.EventHooks;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.common.util.TriState;

import javax.annotation.Nullable;
import java.util.ArrayList;

/**
 * NeoForge's {@link EditAuthority}: the level's fake player stands in for automation, and claim,
 * protection, and logging mods are given the chance to veto through the ordinary NeoForge placement
 * and break events.
 */
public final class NeoForgeEditAuthority implements EditAuthority {
    @Override
    public boolean mayUseAdjacent(ServerPlayer player, BlockPos pos) {
        var event = new PlayerInteractEvent.RightClickBlock(
                player, InteractionHand.MAIN_HAND, pos, WorldEdits.adjacentHit(player, pos));
        NeoForge.EVENT_BUS.post(event);
        return !event.isCanceled()
                && event.getUseBlock() != TriState.FALSE
                && event.getUseItem() != TriState.FALSE;
    }

    @Override
    public ServerPlayer automationActor(ServerLevel level) {
        return FakePlayerFactory.get(level, AutomationActor.PROFILE);
    }

    private static final int UPDATE_RECURSION_LIMIT = 512;
    private boolean placing;

    @Override
    public boolean place(Player placer, ServerLevel level, BlockPos pos,
                         BlockState state, Direction placedAgainst) {
        if (placing || level.captureBlockSnapshots || level.restoringBlockSnapshots) {
            return false;
        }
        placing = true;
        int firstSnapshot = level.capturedBlockSnapshots.size();
        var snapshots = new ArrayList<BlockSnapshot>();
        try {
            boolean placed;
            level.captureBlockSnapshots = true;
            try {
                placed = level.setBlock(pos, state, Block.UPDATE_ALL);
            } finally {
                level.captureBlockSnapshots = false;
                snapshots.addAll(level.capturedBlockSnapshots.subList(
                        firstSnapshot, level.capturedBlockSnapshots.size()));
                level.capturedBlockSnapshots.subList(
                        firstSnapshot, level.capturedBlockSnapshots.size()).clear();
            }
            if (!placed || snapshots.isEmpty()
                    || EventHooks.onBlockPlace(placer, snapshots.get(0), placedAgainst)) {
                level.restoringBlockSnapshots = true;
                try {
                    for (int i = snapshots.size() - 1; i >= 0; i--) {
                        BlockSnapshot snapshot = snapshots.get(i);
                        snapshot.restore(Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
                    }
                } finally {
                    level.restoringBlockSnapshots = false;
                }
                return false;
            }
            for (BlockSnapshot snapshot : snapshots) {
                BlockState previous = snapshot.getState();
                BlockState current = level.getBlockState(snapshot.getPos());
                current.onPlace(level, snapshot.getPos(), previous, false);
                level.markAndNotifyBlock(snapshot.getPos(), level.getChunkAt(snapshot.getPos()),
                        previous, current, snapshot.getFlags(), UPDATE_RECURSION_LIMIT);
            }
            return true;
        } finally {
            placing = false;
        }
    }

    @Override
    public boolean vetoesRemoval(
            ServerPlayer actor, ServerLevel level, BlockPos pos, BlockState state,
            @Nullable BlockEntity blockEntity) {
        BlockEvent.BreakEvent evt = new BlockEvent.BreakEvent(level, pos, state, actor);
        return NeoForge.EVENT_BUS.post(evt).isCanceled();
    }
}
