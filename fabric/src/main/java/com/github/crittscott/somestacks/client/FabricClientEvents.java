package com.github.crittscott.somestacks.client;

import com.github.crittscott.somestacks.client.interaction.InteractionContext;
import com.github.crittscott.somestacks.client.interaction.InteractionRuleRegistry;
import com.github.crittscott.somestacks.server.FabricStackInteractionEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.minecraft.world.InteractionResult;

/** Feeds Fabric interaction callbacks into the shared ordered gesture rules. */
public final class FabricClientEvents {
    private FabricClientEvents() {}

    public static void init() {
        UseBlockCallback.EVENT.register(
                FabricStackInteractionEvents.STACK_GESTURES_PHASE,
                (player, level, hand, hit) -> {
                    if (!level.isClientSide) {
                        return InteractionResult.PASS;
                    }

                    InteractionContext context = InteractionContext.forBlockClick(
                            player, level, hand, hit.getBlockPos(), hit.getDirection(),
                            ClientGestures.currentMode(), FabricKeyMappings.STACK_MODE_KEY.isDown());
                    ClientGestures.syncState(FabricKeyMappings.STACK_MODE_KEY.isDown());
                    InteractionRuleRegistry.processBlockRules(context);
                    // SUCCESS stops local block/item use and still sends vanilla's use packet.
                    return context.shouldCancel()
                            ? InteractionResult.SUCCESS
                            : InteractionResult.PASS;
                });

        UseItemCallback.EVENT.register((player, level, hand) -> {
            if (!level.isClientSide) {
                return InteractionResult.PASS;
            }

            InteractionContext context = InteractionContext.forAirClick(
                    player, level, hand, ClientGestures.currentMode(),
                    FabricKeyMappings.STACK_MODE_KEY.isDown());
            ClientGestures.syncState(FabricKeyMappings.STACK_MODE_KEY.isDown());
            InteractionRuleRegistry.processItemRules(context);
            return context.shouldCancel()
                    ? InteractionResult.FAIL
                    : InteractionResult.PASS;
        });
    }
}
