package com.github.crittscott.somestacks.client;

import com.github.crittscott.somestacks.client.interaction.InteractionContext;
import com.github.crittscott.somestacks.client.interaction.InteractionRuleRegistry;
import com.github.crittscott.somestacks.server.FabricStackInteractionEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.minecraft.client.Minecraft;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

/** Feeds Fabric interaction callbacks into the shared ordered gesture rules. */
public final class FabricClientEvents {
    private FabricClientEvents() {}

    public static void init() {
        UseBlockCallback.EVENT.register(FabricStackInteractionEvents.STACK_GESTURES_PHASE,
                FabricClientEvents::onUseBlock);

        // Claimed ahead of the default phase: an earlier non-PASS listener would end the chain
        // before the gesture is seen. CONSUME ends the client's hand loop without a swing.
        UseItemCallback.EVENT.register(FabricStackInteractionEvents.STACK_MODIFIER_PHASE, (player, level, hand) -> {
            if (!level.isClientSide) {
                return InteractionResultHolder.pass(player.getItemInHand(hand));
            }

            InteractionContext context = InteractionContext.forAirClick(
                    player, level, hand, ClientGestures.currentMode(),
                    FabricKeyMappings.STACK_MODE_KEY.isDown()).withHittingBlock(isHittingBlock());
            if (player.getItemInHand(hand).isEmpty()) {
                InteractionRuleRegistry.processEmptyHandRules(context);
            } else {
                InteractionRuleRegistry.processItemRules(context);
            }
            ClientGestures.syncState(FabricKeyMappings.STACK_MODE_KEY.isDown());
            return context.shouldCancel()
                    ? InteractionResultHolder.consume(player.getItemInHand(hand))
                    : InteractionResultHolder.pass(player.getItemInHand(hand));
        });
    }

    /** SUCCESS suppresses local block/item use while retaining the ordinary Fabric use packet. */
    public static InteractionResult onUseBlock(Player player, Level level, InteractionHand hand, BlockHitResult hit) {
        if (!level.isClientSide) {
            return InteractionResult.PASS;
        }

        InteractionContext context = InteractionContext.forBlockClick(
                player, level, hand, hit.getBlockPos(), hit.getDirection(),
                ClientGestures.currentMode(), FabricKeyMappings.STACK_MODE_KEY.isDown());
        ClientGestures.syncState(FabricKeyMappings.STACK_MODE_KEY.isDown());
        InteractionRuleRegistry.processBlockRules(context);
        return context.shouldCancel() ? InteractionResult.SUCCESS : InteractionResult.PASS;
    }

    private static boolean isHittingBlock() {
        HitResult hit = Minecraft.getInstance().hitResult;
        return hit != null && hit.getType() == HitResult.Type.BLOCK;
    }
}
