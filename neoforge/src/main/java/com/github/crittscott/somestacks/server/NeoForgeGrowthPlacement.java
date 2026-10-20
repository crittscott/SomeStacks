package com.github.crittscott.somestacks.server;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import net.neoforged.neoforge.common.util.BlockSnapshot;
import net.neoforged.neoforge.event.EventHooks;

import java.util.ArrayList;
import java.util.List;

/** A placement accepted by protection, with world publication deferred to a transfer root commit. */
public record NeoForgeGrowthPlacement(ServerLevel level, BlockPos pos, BlockState state) {
    private static final int UPDATE_RECURSION_LIMIT = 512;

    /** Consults the native placement event against a tentative block, then restores it silently. */
    public static NeoForgeGrowthPlacement prepare(ServerLevel level, BlockPos pos, BlockState state) {
        if (level.captureBlockSnapshots || level.restoringBlockSnapshots || !WorldEdits.mayPlace(null, level, pos)) {
            return null;
        }
        List<BlockSnapshot> snapshots = new ArrayList<>();
        try {
            if (!capture(level, pos, state, snapshots, true) || snapshots.isEmpty()
                    || EventHooks.onBlockPlace(WorldEdits.automationActor(level), snapshots.get(0), Direction.DOWN)) {
                return null;
            }
            return new NeoForgeGrowthPlacement(level, pos.immutable(), level.getBlockState(pos));
        } finally {
            restore(level, snapshots);
        }
    }

    /** Installs the accepted block without sending updates; the caller fills it before publication. */
    public List<BlockSnapshot> install() {
        List<BlockSnapshot> snapshots = new ArrayList<>();
        if (!capture(level, pos, state, snapshots, true)) {
            restore(level, snapshots);
            throw new IllegalStateException("Accepted automation growth could not be installed at " + pos);
        }
        return snapshots;
    }

    /** Publishes only after all committed inventory changes have been applied. */
    public void publish(List<BlockSnapshot> snapshots) {
        publishSnapshots(level, snapshots);
        ServerPlayer actor = WorldEdits.automationActor(level);
        BlockState placed = level.getBlockState(pos);
        placed.getBlock().setPlacedBy(level, pos, placed, actor, actor.getMainHandItem());
        level.gameEvent(GameEvent.BLOCK_PLACE, pos, GameEvent.Context.of(actor, placed));
    }

    static void publishSnapshots(ServerLevel level, List<BlockSnapshot> snapshots) {
        for (BlockSnapshot snapshot : snapshots) {
            BlockState previous = snapshot.getState();
            BlockState current = level.getBlockState(snapshot.getPos());
            current.onPlace(level, snapshot.getPos(), previous, false);
            level.markAndNotifyBlock(snapshot.getPos(), level.getChunkAt(snapshot.getPos()),
                    previous, current, Block.UPDATE_ALL, UPDATE_RECURSION_LIMIT);
        }
    }

    static boolean capture(ServerLevel level, BlockPos pos, BlockState state,
                           List<BlockSnapshot> snapshots) {
        return capture(level, pos, state, snapshots, false);
    }

    private static boolean capture(ServerLevel level, BlockPos pos, BlockState state,
                                   List<BlockSnapshot> snapshots, boolean tentative) {
        int first = level.capturedBlockSnapshots.size();
        level.captureBlockSnapshots = true;
        try {
            int flags = Block.UPDATE_ALL;
            if (tentative) flags |= Block.UPDATE_SKIP_ALL_SIDEEFFECTS | Block.UPDATE_SKIP_ON_PLACE;
            return level.setBlock(pos, state, flags);
        } finally {
            level.captureBlockSnapshots = false;
            snapshots.addAll(level.capturedBlockSnapshots.subList(first, level.capturedBlockSnapshots.size()));
            level.capturedBlockSnapshots.subList(first, level.capturedBlockSnapshots.size()).clear();
        }
    }

    static void restore(ServerLevel level, List<BlockSnapshot> snapshots) {
        int first = level.capturedBlockSnapshots.size();
        level.restoringBlockSnapshots = true;
        level.captureBlockSnapshots = true;
        try {
            for (int i = snapshots.size() - 1; i >= 0; i--) {
                BlockSnapshot snapshot = snapshots.get(i);
                level.setBlock(snapshot.getPos(), snapshot.getState(),
                        Block.UPDATE_SKIP_ALL_SIDEEFFECTS | Block.UPDATE_SKIP_ON_PLACE);
                snapshot.restoreBlockEntity(level, snapshot.getPos());
            }
        } finally {
            level.captureBlockSnapshots = false;
            level.capturedBlockSnapshots.subList(first, level.capturedBlockSnapshots.size()).clear();
            level.restoringBlockSnapshots = false;
        }
    }
}
