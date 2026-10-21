package com.github.crittscott.somestacks.client;

import com.github.crittscott.somestacks.util.BlockType;
import com.github.crittscott.somestacks.server.FabricStackInteractionEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.minecraft.client.Minecraft;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;

/** Feeds Fabric interaction callbacks into the shared gesture classifier. */
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

            ClientGestures.syncState(FabricKeyMappings.STACK_MODE_KEY.isDown());
            if (ClientGestures.isHittingBlock(Minecraft.getInstance().hitResult)) return InteractionResult.PASS;
            var gesture = ClientGestures.processClick(player, hand, null, false,
                    FabricKeyMappings.STACK_MODE_KEY.isDown());
            return gesture.consumesLocalUse(player.getMainHandItem())
                    ? InteractionResult.FAIL
                    : InteractionResult.PASS;
        });
    }

    /** Handles Fabric's client block-use callback; SUCCESS consumes local use while retaining the vanilla packet. */
    public static InteractionResult onUseBlock(Player player, Level level, InteractionHand hand, BlockHitResult hit) {
        if (!level.isClientSide) return InteractionResult.PASS;
        ClientGestures.syncState(FabricKeyMappings.STACK_MODE_KEY.isDown());
        var gesture = ClientGestures.processClick(player, hand,
                BlockType.of(level.getBlockState(hit.getBlockPos()).getBlock()), true,
                FabricKeyMappings.STACK_MODE_KEY.isDown());
        return gesture.consumesLocalUse(player.getMainHandItem()) ? InteractionResult.SUCCESS : InteractionResult.PASS;
    }
}
