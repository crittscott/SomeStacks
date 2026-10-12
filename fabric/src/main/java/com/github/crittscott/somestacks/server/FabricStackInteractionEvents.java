package com.github.crittscott.somestacks.server;

import com.github.crittscott.somestacks.SomeStacksCommon;
import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;

/** Runs stack gestures after listeners in Fabric's default protection phase. */
public final class FabricStackInteractionEvents {
    public static final ResourceLocation STACK_GESTURES_PHASE =
            new ResourceLocation(SomeStacksCommon.MODID, "stack_gestures");

    private FabricStackInteractionEvents() {}

    public static void init() {
        UseBlockCallback.EVENT.addPhaseOrdering(Event.DEFAULT_PHASE, STACK_GESTURES_PHASE);
        UseBlockCallback.EVENT.register(STACK_GESTURES_PHASE, (player, level, hand, hit) -> {
            if (level.isClientSide || !(player instanceof ServerPlayer serverPlayer)) return InteractionResult.PASS;
            return StackInteractions.handleBlockClick(serverPlayer, hand, hit, true, true)
                    ? InteractionResult.SUCCESS : InteractionResult.PASS;
        });
    }
}
