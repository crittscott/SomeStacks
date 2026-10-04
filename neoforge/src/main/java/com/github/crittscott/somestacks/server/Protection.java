package com.github.crittscott.somestacks.server;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.TriState;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

/** NeoForge protection consultation for a stack reached through its neighboring block. */
public final class Protection implements AdjacentEditAuthority {
    private static final InteractionHand GESTURE_HAND = InteractionHand.MAIN_HAND;

    @Override
    public boolean mayUseItemAt(ServerPlayer player, BlockPos pos) {
        PlayerInteractEvent.RightClickBlock event = new PlayerInteractEvent.RightClickBlock(
                player, GESTURE_HAND, pos, AdjacentEdits.lookHit(player, pos));
        NeoForge.EVENT_BUS.post(event);
        return !event.isCanceled()
                && event.getUseBlock() != TriState.FALSE
                && event.getUseItem() != TriState.FALSE;
    }
}
