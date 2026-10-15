package com.github.crittscott.somestacks.server;

import com.github.crittscott.somestacks.SomeStacks;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Applies the gestures vanilla does not route to a stack block's own use method (sneaking torch
 * rotations and clicks on a neighboring block) after Forge's ordinary right-click protection
 * listeners, and keeps a Stack Modifier item use from reaching the held item.
 */
@Mod.EventBusSubscriber(modid = SomeStacks.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class StackInteractionEvents {
    private StackInteractionEvents() {}

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (event.getLevel().isClientSide() || event.isCanceled()
                || !(event.getEntity() instanceof ServerPlayer player)) return;
        boolean blockAllowed = event.getUseBlock() != Event.Result.DENY;
        boolean itemAllowed = event.getUseItem() != Event.Result.DENY;
        if (StackInteractions.handleSneakingRotation(
                        player, event.getHand(), event.getHitVec(), blockAllowed, itemAllowed)
                || StackInteractions.handleAdjacentClick(
                        player, event.getHand(), event.getHitVec(), blockAllowed, itemAllowed)) {
            event.setCanceled(true);
            event.setUseBlock(Event.Result.DENY);
            event.setUseItem(Event.Result.DENY);
            event.setCancellationResult(InteractionResult.SUCCESS);
        }
    }

    /** Refuses the item use a Stack Modifier air click still sends, before other mods act on it. */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onRightClickItem(PlayerInteractEvent.RightClickItem event) {
        if (event.getLevel().isClientSide()
                || !(event.getEntity() instanceof ServerPlayer player)) return;
        if (StackInteractions.modifierClaimsItemUse(player, event.getHand())) {
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.FAIL);
        }
    }
}
