package com.github.crittscott.somestacks.mixin;

import com.github.crittscott.somestacks.client.ClientGestures;
import com.github.crittscott.somestacks.client.FabricKeyMappings;
import com.github.crittscott.somestacks.client.interaction.InteractionContext;
import com.github.crittscott.somestacks.client.interaction.InteractionRuleRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.HitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import javax.annotation.Nullable;

/** Supplies Fabric's missing empty-hand, open-air right-click callback. */
@Mixin(Minecraft.class)
abstract class MinecraftMixin {
    @Shadow @Nullable public LocalPlayer player;
    @Shadow @Nullable public HitResult hitResult;

    @Inject(method = "startUseItem", at = @At("HEAD"))
    private void somestacks$onEmptyHandAirClick(CallbackInfo callback) {
        if (player == null
                || !player.getMainHandItem().isEmpty()
                || !player.getOffhandItem().isEmpty()
                || hitResult == null
                || hitResult.getType() != HitResult.Type.MISS) {
            return;
        }

        InteractionContext context = InteractionContext.forAirClick(
                player, player.level(), InteractionHand.MAIN_HAND,
                ClientGestures.currentMode(), FabricKeyMappings.STACK_MODE_KEY.isDown());
        InteractionRuleRegistry.processEmptyHandRules(context);
    }
}
