package com.github.crittscott.somestacks.mixin;

import com.github.crittscott.somestacks.client.ClientGestures;
import com.github.crittscott.somestacks.client.FabricClientRendering;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.HitResult;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import javax.annotation.Nullable;

/**
 * Supplies Fabric's missing empty-hand, open-air right-click callback. Fabric has no event for this
 * click, and vanilla reaches {@code UseItemCallback} only for a nonempty hand. The injection sits at
 * the head of {@code startUseItem}, before vanilla's own gates, so it applies them itself: no
 * right-click acts while a block is being mined or while the hands are busy, as when steering a
 * boat.
 */
@Mixin(Minecraft.class)
abstract class MinecraftMixin {
    @Shadow @Nullable public MultiPlayerGameMode gameMode;
    @Shadow @Nullable public HitResult hitResult;
    @Shadow @Nullable public LocalPlayer player;

    @Inject(method = "startUseItem", at = @At("HEAD"))
    private void somestacks$onEmptyHandAirClick(CallbackInfo callback) {
        if (player == null
                || gameMode == null
                || gameMode.isDestroying()
                || player.isHandsBusy()
                || !ClientGestures.isEmptyMainHandAirClick(player, hitResult)) {
            return;
        }

        ClientGestures.syncState(FabricClientRendering.STACK_MODE_KEY.isDown());
        ClientGestures.processClick(
                player,
                InteractionHand.MAIN_HAND,
                null,
                false,
                FabricClientRendering.STACK_MODE_KEY.isDown());
    }
}
