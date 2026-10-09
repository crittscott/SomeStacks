package com.github.crittscott.somestacks.server;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.util.BlockSnapshot;
import net.minecraftforge.event.ForgeEventFactory;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.Event;

import java.util.HashMap;
import java.util.Map;
import javax.annotation.Nullable;
import java.util.ArrayList;

/**
 * Forge's {@link EditAuthority}: a synthetic per-level {@link ServerPlayer} stands in for
 * automation, and claim, protection, and logging mods are given the chance to veto through the
 * ordinary Forge placement and break events. Forge ships no FakePlayer helper, so this constructs
 * a connection-safe {@code ServerPlayer} and caches one actor per dimension.
 */
public final class ForgeEditAuthority implements EditAuthority {
    @Override
    public boolean mayUseAdjacent(ServerPlayer player, BlockPos pos) {
        var event = new PlayerInteractEvent.RightClickBlock(
                player, InteractionHand.MAIN_HAND, pos, WorldEdits.adjacentHit(player, pos));
        MinecraftForge.EVENT_BUS.post(event);
        return !event.isCanceled()
                && event.getUseBlock() != Event.Result.DENY
                && event.getUseItem() != Event.Result.DENY;
    }

    private final Map<ResourceKey<Level>, ServerPlayer> actors = new HashMap<>();

    @Override
    public ServerPlayer automationActor(ServerLevel level) {
        return actors.computeIfAbsent(level.dimension(), key ->
                new AutomationPlayer(level));
    }

    /** Forge has no fake-player facility; automation must not try to write to a missing connection. */
    private static final class AutomationPlayer extends ServerPlayer {
        private AutomationPlayer(ServerLevel level) {
            super(level.getServer(), level, AutomationActor.PROFILE,
                    ClientInformation.createDefault());
        }

        @Override
        public void sendSystemMessage(Component message) {
        }

        @Override
        public void displayClientMessage(Component message, boolean actionBar) {
        }
    }

    /** Releases the actor that retains an unloading level. */
    public void unload(ServerLevel level) {
        actors.remove(level.dimension());
    }

    /** Releases every actor after a server session ends. */
    public void clear() {
        actors.clear();
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
                    || ForgeEventFactory.onBlockPlace(placer, snapshots.get(0), placedAgainst)) {
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
    public boolean vetoesRemoval(
            ServerPlayer actor, ServerLevel level, BlockPos pos, BlockState state,
            @Nullable BlockEntity blockEntity) {
        BlockEvent.BreakEvent evt = new BlockEvent.BreakEvent(level, pos, state, actor);
        return MinecraftForge.EVENT_BUS.post(evt);
    }
}
