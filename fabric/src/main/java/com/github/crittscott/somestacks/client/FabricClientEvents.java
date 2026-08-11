package com.github.crittscott.somestacks.client;

import com.github.crittscott.somestacks.client.interaction.InteractionContext;
import com.github.crittscott.somestacks.client.interaction.InteractionRuleRegistry;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;

/** Feeds Fabric interaction callbacks into the shared ordered gesture rules. */
public final class FabricClientEvents {
    private FabricClientEvents() {}

    public static void init() {
        UseBlockCallback.EVENT.register((player, level, hand, hit) -> {
            if (!level.isClientSide) {
                return InteractionResult.PASS;
            }

            InteractionContext context = InteractionContext.forBlockClick(
                    player, level, hand, hit.getBlockPos(), hit.getDirection(),
                    ClientGestures.currentMode(), FabricKeyMappings.STACK_MODE_KEY.isDown());
            InteractionRuleRegistry.processBlockRules(context);
            return context.shouldCancel() ? InteractionResult.FAIL : InteractionResult.PASS;
        });

        UseItemCallback.EVENT.register((player, level, hand) -> {
            if (!level.isClientSide) {
                return InteractionResultHolder.pass(player.getItemInHand(hand));
            }

            InteractionContext context = InteractionContext.forAirClick(
                    player, level, hand, ClientGestures.currentMode(),
                    FabricKeyMappings.STACK_MODE_KEY.isDown());
            if (player.getItemInHand(hand).isEmpty()) {
                InteractionRuleRegistry.processEmptyHandRules(context);
            } else {
                InteractionRuleRegistry.processItemRules(context);
            }
            return context.shouldCancel()
                    ? InteractionResultHolder.fail(player.getItemInHand(hand))
                    : InteractionResultHolder.pass(player.getItemInHand(hand));
        });
    }
}
