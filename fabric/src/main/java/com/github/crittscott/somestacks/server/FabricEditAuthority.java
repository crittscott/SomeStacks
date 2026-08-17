package com.github.crittscott.somestacks.server;

import com.mojang.authlib.GameProfile;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.state.BlockState;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Fabric's {@link EditAuthority}: the automation actor is a synthetic {@link ServerPlayer}, and
 * removal answers to Fabric API's block-break event, the counterpart to Forge's block-break event,
 * so claim and protection mods can veto automation-driven removal the same way they can on Forge.
 *
 * <p>Placement has no comparable vanilla click to consult: automated growth has no player gesture
 * for the mod's placement-protection hook ({@link FabricPlayerEditAuthority}) to fire, and Fabric
 * API has no generic "a block was placed" event the way Forge's block-place event is. FTB Chunks
 * is consulted directly instead, through {@link FtbChunksProtection}, when it is installed; other
 * claim mods are not covered.
 */
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
        if (!FtbChunksProtection.isLoaded()) {
            return (placer, placedAgainst) -> false;
        }
        return (placer, placedAgainst) -> FtbChunksProtection.prevents((ServerPlayer) placer, pos);
    }

    @Override
    public boolean vetoesRemoval(ServerLevel level, BlockPos pos, BlockState state) {
        ServerPlayer breaker = automationActor(level);
        return !PlayerBlockBreakEvents.BEFORE.invoker()
                .beforeBlockBreak(level, breaker, pos, state, level.getBlockEntity(pos));
    }
}
