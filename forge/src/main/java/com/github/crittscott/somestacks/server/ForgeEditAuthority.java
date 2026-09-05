package com.github.crittscott.somestacks.server;

import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.util.BlockSnapshot;
import net.minecraftforge.event.ForgeEventFactory;
import net.minecraftforge.event.level.BlockEvent;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;

/**
 * Forge's {@link EditAuthority}: a synthetic per-level {@link ServerPlayer} stands in for
 * automation, and claim, protection, and logging mods are given the chance to veto through the
 * ordinary Forge placement and break events. Forge 1.21.1 no longer ships a FakePlayer helper, so
 * this constructs a bare {@code ServerPlayer} the same way the Fabric authority does.
 */
public final class ForgeEditAuthority implements EditAuthority {
    private static final GameProfile PROFILE = new GameProfile(
            UUID.nameUUIDFromBytes("somestacks:automation".getBytes(StandardCharsets.UTF_8)),
            "[SomeStacks]");

    private final Map<ServerLevel, ServerPlayer> actors = new WeakHashMap<>();

    @Override
    public ServerPlayer automationActor(ServerLevel level) {
        return actors.computeIfAbsent(level, key ->
                new ServerPlayer(key.getServer(), key, PROFILE, ClientInformation.createDefault()));
    }

    @Override
    public EditAuthority.PlacementVeto preparePlacement(ServerLevel level, BlockPos pos) {
        BlockSnapshot snapshot = BlockSnapshot.create(level.dimension(), level, pos);
        return (placer, placedAgainst) -> ForgeEventFactory.onBlockPlace(placer, snapshot, placedAgainst);
    }

    @Override
    public boolean vetoesRemoval(ServerLevel level, BlockPos pos, BlockState state) {
        ServerPlayer breaker = automationActor(level);
        BlockEvent.BreakEvent evt = new BlockEvent.BreakEvent(level, pos, state, breaker);
        return MinecraftForge.EVENT_BUS.post(evt);
    }
}
