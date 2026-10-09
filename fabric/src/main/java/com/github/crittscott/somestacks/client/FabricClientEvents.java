package com.github.crittscott.somestacks.client;

import com.github.crittscott.somestacks.client.interaction.InteractionContext;
import com.github.crittscott.somestacks.client.interaction.InteractionRuleRegistry;
import com.github.crittscott.somestacks.server.FabricStackInteractionEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.minecraft.client.Minecraft;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;

/** Feeds Fabric interaction callbacks into the shared ordered gesture rules. */
public final class FabricClientEvents {
    private FabricClientEvents() {}

    public static void init() {
        UseBlockCallback.EVENT.register(
                FabricStackInteractionEvents.STACK_GESTURES_PHASE,
                FabricClientEvents::onUseBlock);

        UseItemCallback.EVENT.register((player, level, hand) -> {
            if (!level.isClientSide) {
                return InteractionResult.PASS;
            }

            InteractionContext context = InteractionContext.forAirClick(
                    player, level, hand, ClientGestures.currentMode(),
                    FabricKeyMappings.STACK_MODE_KEY.isDown(),
                    ClientGestures.isHittingBlock(Minecraft.getInstance().hitResult));
            ClientGestures.syncState(FabricKeyMappings.STACK_MODE_KEY.isDown());
            InteractionRuleRegistry.processItemRules(context);
            return context.shouldCancel()
                    ? InteractionResult.FAIL
                    : InteractionResult.PASS;
        });
    }

    /** Handles Fabric's client block-use callback; SUCCESS consumes local use while retaining the vanilla packet. */
    public static InteractionResult onUseBlock(Player player, Level level, InteractionHand hand, BlockHitResult hit) {
        if (!level.isClientSide) return InteractionResult.PASS;
        InteractionContext context = InteractionContext.forBlockClick(
                player, level, hand, hit.getBlockPos(), hit.getDirection(),
                ClientGestures.currentMode(), FabricKeyMappings.STACK_MODE_KEY.isDown());
        ClientGestures.syncState(FabricKeyMappings.STACK_MODE_KEY.isDown());
        InteractionRuleRegistry.processBlockRules(context);
        return context.shouldCancel() ? InteractionResult.SUCCESS : InteractionResult.PASS;
    }
}
