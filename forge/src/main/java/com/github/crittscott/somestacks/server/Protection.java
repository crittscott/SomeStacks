package com.github.crittscott.somestacks.server;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.Event;

/** Forge player protection through the native right-click event and trailing-click suppressor. */
public final class Protection extends EventPlayerEditAuthority {
    private static final InteractionHand GESTURE_HAND = InteractionHand.MAIN_HAND;

    @Override
    protected boolean fireRightClick(
            ServerPlayer player, BlockPos pos, boolean requireItemUse) {
        PlayerInteractEvent.RightClickBlock event = new PlayerInteractEvent.RightClickBlock(
                player, GESTURE_HAND, pos, lookHit(player, pos));
        MinecraftForge.EVENT_BUS.post(event);
        return !event.isCanceled()
                && event.getUseBlock() != Event.Result.DENY
                && (!requireItemUse || event.getUseItem() != Event.Result.DENY);
    }

    @Override
    protected void markClaim(ServerPlayer player, BlockPos pos) {
        RightClickBlockSuppressor.suppress(player, pos, player.level());
    }

    /** Exposes the held-item half of the native event for its loader-local protection test. */
    public boolean mayUseItemOn(ServerPlayer player, BlockPos pos) {
        return fireRightClick(player, pos, true);
    }
}
