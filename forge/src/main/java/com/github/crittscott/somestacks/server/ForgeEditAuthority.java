package com.github.crittscott.somestacks.server;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.util.BlockSnapshot;
import net.minecraftforge.event.ForgeEventFactory;
import net.minecraftforge.event.level.BlockEvent;

import java.util.HashMap;
import java.util.Map;
import javax.annotation.Nullable;

/**
 * Forge's {@link EditAuthority}: a synthetic per-level {@link ServerPlayer} stands in for
 * automation, and claim, protection, and logging mods are given the chance to veto through the
 * ordinary Forge placement and break events. Forge ships no FakePlayer helper, so this constructs
 * a bare {@code ServerPlayer} and caches one actor per dimension.
 */
public final class ForgeEditAuthority implements EditAuthority {
    private final Map<ResourceKey<Level>, ServerPlayer> actors = new HashMap<>();

    @Override
    public ServerPlayer automationActor(ServerLevel level) {
        return actors.computeIfAbsent(level.dimension(), key ->
                new ServerPlayer(level.getServer(), level, AutomationActor.PROFILE,
                        ClientInformation.createDefault()));
    }

    /** Releases the actor that retains an unloading level. */
    public void unload(ServerLevel level) {
        actors.remove(level.dimension());
    }

    /** Releases every actor after a server session ends. */
    public void clear() {
        actors.clear();
    }

    @Override
    public EditAuthority.PlacementVeto preparePlacement(ServerLevel level, BlockPos pos) {
        BlockSnapshot snapshot = BlockSnapshot.create(level.dimension(), level, pos);
        return new EditAuthority.PlacementVeto() {
            @Override
            public boolean isVetoedAfter(Player placer, Direction placedAgainst) {
                return ForgeEventFactory.onBlockPlace(placer, snapshot, placedAgainst);
            }
        };
    }

    @Override
    public boolean vetoesRemoval(
            ServerPlayer actor, ServerLevel level, BlockPos pos, BlockState state,
            @Nullable BlockEntity blockEntity) {
        BlockEvent.BreakEvent evt = new BlockEvent.BreakEvent(level, pos, state, actor);
        return MinecraftForge.EVENT_BUS.post(evt);
    }
}
