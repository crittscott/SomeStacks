package com.github.crittscott.somestacks.server;

import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;

/** Fabric player protection through {@link UseBlockCallback}. */
public final class FabricPlayerEditAuthority extends EventPlayerEditAuthority {
    private static final InteractionHand GESTURE_HAND = InteractionHand.MAIN_HAND;

    @Override
    protected boolean fireRightClick(
            ServerPlayer player, BlockPos pos, boolean requireItemUse) {
        InteractionResult result = UseBlockCallback.EVENT.invoker()
                .interact(player, player.level(), GESTURE_HAND, lookHit(player, pos));
        return result != InteractionResult.FAIL;
    }

    @Override
    protected void markClaim(ServerPlayer player, BlockPos pos) {
        // Fabric cancels the client callback that recognized the gesture, so no vanilla packet follows.
    }
}
