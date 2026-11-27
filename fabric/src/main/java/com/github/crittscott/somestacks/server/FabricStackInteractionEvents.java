package com.github.crittscott.somestacks.server;

import com.github.crittscott.somestacks.SomeStacksCommon;

import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;

/** Applies Some Stacks gestures from Fabric's actual server-side block-use callback. */
public final class FabricStackInteractionEvents {
    /**
     * The block-use phase, ordered after the default one, in which the mod's server and client
     * gesture listeners run.
     */
    public static final Identifier STACK_GESTURES_PHASE =
            Identifier.fromNamespaceAndPath(SomeStacksCommon.MODID, "stack_gestures");

    private FabricStackInteractionEvents() {}

    /**
     * Registers the server gesture listener for sneaking rotation and adjacent clicks in {@link
     * #STACK_GESTURES_PHASE}, so default-phase listeners, such as protection mods, decide first
     * and a click one of them handles never reaches it.
     */
    public static void init() {
        UseBlockCallback.EVENT.addPhaseOrdering(Event.DEFAULT_PHASE, STACK_GESTURES_PHASE);
        UseBlockCallback.EVENT.register(
                STACK_GESTURES_PHASE,
                (player, level, hand, hit) -> {
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
