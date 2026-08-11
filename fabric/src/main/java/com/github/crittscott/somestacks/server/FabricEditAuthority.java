package com.github.crittscott.somestacks.server;

import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.state.BlockState;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.WeakHashMap;

/** Fabric's vanilla-only automation actor and edit authority. */
public final class FabricEditAuthority implements EditAuthority {
    private static final GameProfile PROFILE = new GameProfile(
            java.util.UUID.nameUUIDFromBytes("somestacks:fabric_automation".getBytes(StandardCharsets.UTF_8)),
            "[SomeStacks]");

    private final Map<ServerLevel, ServerPlayer> actors = new WeakHashMap<>();

    @Override
    public ServerPlayer automationActor(ServerLevel level) {
        return actors.computeIfAbsent(level,
                key -> new ServerPlayer(key.getServer(), key, PROFILE));
    }

    @Override
    public PlacementVeto preparePlacement(ServerLevel level, BlockPos pos) {
        return (placer, placedAgainst) -> false;
    }

    @Override
    public boolean vetoesRemoval(ServerLevel level, BlockPos pos, BlockState state) {
        return false;
    }
}
