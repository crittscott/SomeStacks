package com.github.crittscott.somestacks.server;

import com.github.crittscott.somestacks.SomeStacksCommon;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.common.util.Result;
import net.minecraftforge.eventbus.api.listener.Priority;
import net.minecraftforge.eventbus.api.listener.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Applies Some Stacks gestures from Forge's actual server-side right-click event. */
@Mod.EventBusSubscriber(modid = SomeStacksCommon.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class ForgeStackInteractionEvents {
    private ForgeStackInteractionEvents() {}

    @SubscribeEvent(priority = Priority.LOWEST)
    public static boolean onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (event.getLevel().isClientSide()) {
            return false;
        }

        ServerPlayer player = (ServerPlayer) event.getEntity();
        boolean blockAllowed = event.getUseBlock() != Result.DENY;
        boolean itemAllowed = event.getUseItem() != Result.DENY;
        boolean handled = StackInteractions.handleSneakingRotation(
                player, event.getHand(), event.getHitVec(), blockAllowed, itemAllowed)
                || StackInteractions.handleAdjacentClick(
                        player, event.getHand(), event.getHitVec(), blockAllowed, itemAllowed);
        if (handled) {
            event.setUseBlock(Result.DENY);
            event.setUseItem(Result.DENY);
            event.setCancellationResult(InteractionResult.SUCCESS);
        }
        return handled;
    }
}
