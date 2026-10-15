package com.github.crittscott.somestacks.server;

import com.github.crittscott.somestacks.SomeStacksCommon;
import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.item.ItemStack;

/**
 * Runs the gestures vanilla does not route to a stack block's own use method (sneaking torch
 * rotations and clicks on a neighboring block) after listeners in Fabric's default protection
 * phase, and refuses a Stack Modifier item use before any default-phase listener sees it.
 */
public final class FabricStackInteractionEvents {
    public static final ResourceLocation STACK_GESTURES_PHASE =
            new ResourceLocation(SomeStacksCommon.MODID, "stack_gestures");
    /** Item-use phase ahead of the default one, so a Stack Modifier click is claimed first. */
    public static final ResourceLocation STACK_MODIFIER_PHASE =
            new ResourceLocation(SomeStacksCommon.MODID, "stack_modifier");

    private FabricStackInteractionEvents() {}

    public static void init() {
        UseItemCallback.EVENT.addPhaseOrdering(STACK_MODIFIER_PHASE, Event.DEFAULT_PHASE);
        UseItemCallback.EVENT.register(STACK_MODIFIER_PHASE, (player, level, hand) -> {
            ItemStack held = player.getItemInHand(hand);
            return !level.isClientSide && player instanceof ServerPlayer serverPlayer
                    && StackInteractions.modifierClaimsItemUse(serverPlayer, hand)
                    ? InteractionResultHolder.fail(held)
                    : InteractionResultHolder.pass(held);
        });

        UseBlockCallback.EVENT.addPhaseOrdering(Event.DEFAULT_PHASE, STACK_GESTURES_PHASE);
        UseBlockCallback.EVENT.register(STACK_GESTURES_PHASE, (player, level, hand, hit) -> {
            if (level.isClientSide || !(player instanceof ServerPlayer serverPlayer)) return InteractionResult.PASS;
            return StackInteractions.handleSneakingRotation(serverPlayer, hand, hit, true, true)
                    || StackInteractions.handleAdjacentClick(serverPlayer, hand, hit, true, true)
                    ? InteractionResult.SUCCESS : InteractionResult.PASS;
        });
    }
}
