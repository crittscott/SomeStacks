package com.github.crittscott.somestacks.server;

import java.util.ArrayList;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.entity.BlockEntity;
import javax.annotation.Nullable;
import net.minecraftforge.common.ForgeHooks;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.util.BlockSnapshot;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.event.ForgeEventFactory;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.Event;

/**
 * Forge's {@link EditAuthority}: the level's fake player stands in for automation, and claim,
 * protection, and logging mods are given the chance to veto through the ordinary Forge placement
 * and break events.
 */
public final class ForgeEditAuthority implements EditAuthority {
    @Override
    public boolean mayUseAdjacent(ServerPlayer player, BlockPos pos) {
        PlayerInteractEvent.RightClickBlock event = ForgeHooks.onRightClickBlock(
                player, InteractionHand.MAIN_HAND, pos, WorldEdits.adjacentHit(player, pos));
        return !event.isCanceled() && event.getUseBlock() != Event.Result.DENY
                && event.getUseItem() != Event.Result.DENY;
    }

    @Override
    public ServerPlayer automationActor(ServerLevel level) {
        return FakePlayerFactory.getMinecraft(level);
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
                    || (snapshots.size() == 1
                        ? ForgeEventFactory.onBlockPlace(placer, snapshots.get(0), placedAgainst)
                        : ForgeEventFactory.onMultiBlockPlace(placer, snapshots, placedAgainst))) {
                level.restoringBlockSnapshots = true;
                level.captureBlockSnapshots = true;
                int rollbackStart = level.capturedBlockSnapshots.size();
                try {
                    for (int i = snapshots.size() - 1; i >= 0; i--) {
                        BlockSnapshot snapshot = snapshots.get(i);
                        snapshot.restore(true, false);
                    }
                } finally {
                    level.captureBlockSnapshots = false;
                    level.capturedBlockSnapshots.subList(
                            rollbackStart, level.capturedBlockSnapshots.size()).clear();
                    level.restoringBlockSnapshots = false;
                }
                return false;
            }
            for (BlockSnapshot snapshot : snapshots) {
                BlockState previous = snapshot.getReplacedBlock();
                BlockState current = level.getBlockState(snapshot.getPos());
                current.onPlace(level, snapshot.getPos(), previous, false);
                level.markAndNotifyBlock(snapshot.getPos(), level.getChunkAt(snapshot.getPos()),
                        previous, current, snapshot.getFlag(), UPDATE_RECURSION_LIMIT);
            }
            return true;
        } finally {
            placing = false;
        }
    }

    @Override
    public boolean vetoesRemoval(ServerLevel level, ServerPlayer actor, BlockPos pos,
                                 BlockState state, @Nullable BlockEntity blockEntity) {
        BlockEvent.BreakEvent evt = new BlockEvent.BreakEvent(level, pos, state, actor);
        return MinecraftForge.EVENT_BUS.post(evt);
    }
}
