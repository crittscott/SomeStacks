package com.github.crittscott.somestacks.mixin;

import com.github.crittscott.somestacks.client.ClientGestures;
import com.github.crittscott.somestacks.client.FabricClientRendering;
import com.github.crittscott.somestacks.client.interaction.InteractionContext;
import com.github.crittscott.somestacks.client.interaction.InteractionRuleRegistry;

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
 * Supplies Fabric's missing empty-main-hand, open-air right-click callback: vanilla reaches the
 * item-use path, where Fabric fires {@code UseItemCallback}, only for a nonempty hand, so no Fabric
 * callback sees an empty main hand clicking air. The hook reads only the main hand and runs before
 * vanilla tries either hand, so an off-hand item does not keep the gesture from acting.
 *
 * <p>The injection sits at the head of {@code startUseItem}, before vanilla's own gates, so it
 * applies them itself: no right-click acts while the player is breaking a block or has busy hands
 * (steering a boat, for example), as on the item path and on Forge.
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
                || !player.getMainHandItem().isEmpty()
                || hitResult == null
                || hitResult.getType() != HitResult.Type.MISS) {
            return;
        }

        InteractionContext context =
                InteractionContext.forAirClick(
                        player,
                        InteractionHand.MAIN_HAND,
                        ClientGestures.currentMode(),
                        FabricClientRendering.STACK_MODE_KEY.isDown());
        InteractionRuleRegistry.processAirRules(context);
        ClientGestures.syncState(FabricClientRendering.STACK_MODE_KEY.isDown());
    }
}
