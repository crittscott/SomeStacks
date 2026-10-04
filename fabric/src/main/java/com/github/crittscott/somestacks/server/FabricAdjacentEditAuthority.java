package com.github.crittscott.somestacks.server;

import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;

/** Fabric protection consultation for a stack reached through its neighboring block. */
public final class FabricAdjacentEditAuthority implements AdjacentEditAuthority {
    private static final InteractionHand GESTURE_HAND = InteractionHand.MAIN_HAND;

    @Override
    public boolean mayUseItemAt(ServerPlayer player, BlockPos pos) {
        InteractionResult result = UseBlockCallback.EVENT.invoker()
                .interact(player, player.level(), GESTURE_HAND, AdjacentEdits.lookHit(player, pos));
        // PASS means no listener claimed or denied this synthetic destination consultation.
        // SUCCESS may represent a listener that already performed an action, so do not stack our
        // deposit on top of it.
        return result == InteractionResult.PASS;
    }
}
