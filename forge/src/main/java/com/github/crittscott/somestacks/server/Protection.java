package com.github.crittscott.somestacks.server;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.Event;

/** Forge protection consultation for a stack reached through its neighboring block. */
public final class Protection implements AdjacentEditAuthority {
    private static final InteractionHand GESTURE_HAND = InteractionHand.MAIN_HAND;

    @Override
    public boolean mayUseItemAt(ServerPlayer player, BlockPos pos) {
        PlayerInteractEvent.RightClickBlock event = new PlayerInteractEvent.RightClickBlock(
                player, GESTURE_HAND, pos, AdjacentEdits.lookHit(player, pos));
        MinecraftForge.EVENT_BUS.post(event);
        return !event.isCanceled()
                && event.getUseBlock() != Event.Result.DENY
                && event.getUseItem() != Event.Result.DENY;
    }
}
