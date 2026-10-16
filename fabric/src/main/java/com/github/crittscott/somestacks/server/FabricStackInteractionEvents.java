package com.github.crittscott.somestacks.server;

import com.github.crittscott.somestacks.SomeStacksCommon;
import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;

/** Applies Some Stacks gestures from Fabric's actual server-side block-use callback. */
public final class FabricStackInteractionEvents {
    public static final Identifier STACK_GESTURES_PHASE =
            Identifier.fromNamespaceAndPath(SomeStacksCommon.MODID, "stack_gestures");

    private FabricStackInteractionEvents() {}

    public static void init() {
        UseBlockCallback.EVENT.addPhaseOrdering(Event.DEFAULT_PHASE, STACK_GESTURES_PHASE);
        UseBlockCallback.EVENT.register(STACK_GESTURES_PHASE, (player, level, hand, hit) -> {
            if (level.isClientSide()) {
                return InteractionResult.PASS;
            }
            ServerPlayer serverPlayer = (ServerPlayer) player;
            return (StackInteractions.handleSneakingRotation(
                            serverPlayer, hand, hit, true, true)
                    || StackInteractions.handleAdjacentClick(
                            serverPlayer, hand, hit, true, true))
                    ? InteractionResult.SUCCESS
                    : InteractionResult.PASS;
        });
    }
}
