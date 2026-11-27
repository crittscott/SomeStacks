package com.github.crittscott.somestacks.server;

import com.github.crittscott.somestacks.SomeStacks;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Applies Some Stacks gestures from Forge's actual server-side right-click event. */
@Mod.EventBusSubscriber(modid = SomeStacks.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class StackInteractionEvents {
    private StackInteractionEvents() {}

    /**
     * Handles sneaking torch rotation and clicks that reach a stack through a neighboring block,
     * passing on the block and item denials earlier listeners set. Runs at {@code LOWEST} so
     * protection mods and other listeners decide first; a click an earlier listener cancels never
     * arrives.
     */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (event.getLevel().isClientSide()) {
            return;
        }

        ServerPlayer player = (ServerPlayer) event.getEntity();
        boolean blockAllowed = event.getUseBlock() != Event.Result.DENY;
        boolean itemAllowed = event.getUseItem() != Event.Result.DENY;
        boolean handled =
                StackInteractions.handleSneakingRotation(
                                player,
                                event.getHand(),
                                event.getHitVec(),
                                blockAllowed,
                                itemAllowed)
                        || StackInteractions.handleAdjacentClick(
                                player,
                                event.getHand(),
                                event.getHitVec(),
                                blockAllowed,
                                itemAllowed);
        if (handled) {
            event.setCanceled(true);
            event.setUseBlock(Event.Result.DENY);
            event.setUseItem(Event.Result.DENY);
            event.setCancellationResult(InteractionResult.SUCCESS);
        }
    }
}
