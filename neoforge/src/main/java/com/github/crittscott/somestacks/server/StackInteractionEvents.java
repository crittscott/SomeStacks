package com.github.crittscott.somestacks.server;

import com.github.crittscott.somestacks.SomeStacksNeoForge;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.util.TriState;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

/** Applies Some Stacks gestures from NeoForge's actual server-side right-click event. */
@EventBusSubscriber(modid = SomeStacksNeoForge.MODID)
public final class StackInteractionEvents {
    private StackInteractionEvents() {}

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (event.getLevel().isClientSide()
                || event.isCanceled()
                || !(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }

        boolean blockAllowed = event.getUseBlock() != TriState.FALSE;
        boolean itemAllowed = event.getUseItem() != TriState.FALSE;
        boolean handled = StackInteractions.handleSneakingRotation(
                player, event.getHand(), event.getHitVec(), blockAllowed, itemAllowed)
                || StackInteractions.handleAdjacentClick(
                        player, event.getHand(), event.getHitVec(), blockAllowed, itemAllowed);
        if (handled) {
            event.setCanceled(true);
            event.setUseBlock(TriState.FALSE);
            event.setUseItem(TriState.FALSE);
            event.setCancellationResult(InteractionResult.SUCCESS);
        }
    }
}
