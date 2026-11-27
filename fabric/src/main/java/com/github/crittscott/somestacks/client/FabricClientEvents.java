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

/** Feeds Fabric interaction callbacks into the shared ordered gesture rules. */
public final class FabricClientEvents {
    private FabricClientEvents() {}

    /**
     * Registers the client block-use listener in the shared gesture phase and the item-use listener
     * for air clicks with a held item. Runs once from the client entry point.
     */
    public static void init() {
        UseBlockCallback.EVENT.register(
                FabricStackInteractionEvents.STACK_GESTURES_PHASE, FabricClientEvents::onUseBlock);

        // Vanilla reaches UseItemCallback only for a nonempty hand; empty-hand air clicks arrive
        // through MinecraftMixin.
        UseItemCallback.EVENT.register((player, level, hand) -> {
            if (!level.isClientSide) {
                return InteractionResultHolder.pass(player.getItemInHand(hand));
            }

            InteractionContext context =
                    InteractionContext.forAirClick(
                            player,
                            hand,
                            ClientGestures.currentMode(),
                            FabricClientRendering.STACK_MODE_KEY.isDown(),
                            ClientGestures.isHittingBlock(Minecraft.getInstance().hitResult));
            ClientGestures.syncState(FabricClientRendering.STACK_MODE_KEY.isDown());
            InteractionRuleRegistry.processAirRules(context);
            return context.shouldCancel()
                    ? InteractionResultHolder.fail(player.getItemInHand(hand))
                    : InteractionResultHolder.pass(player.getItemInHand(hand));
        });
    }

    /**
     * Handles Fabric's client block-use callback; SUCCESS consumes local use while retaining the
     * vanilla packet.
     */
    public static InteractionResult onUseBlock(
            Player player, Level level, InteractionHand hand, BlockHitResult hit) {
        if (!level.isClientSide) return InteractionResult.PASS;
        InteractionContext context =
                InteractionContext.forBlockClick(
                        player,
                        level,
                        hand,
                        hit.getBlockPos(),
                        ClientGestures.currentMode(),
                        FabricClientRendering.STACK_MODE_KEY.isDown());
        ClientGestures.syncState(FabricClientRendering.STACK_MODE_KEY.isDown());
        InteractionRuleRegistry.processBlockRules(context);
        return context.shouldCancel() ? InteractionResult.SUCCESS : InteractionResult.PASS;
    }
}
