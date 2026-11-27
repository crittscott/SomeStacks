package com.github.crittscott.somestacks.gametest;

import com.github.crittscott.somestacks.client.FabricClientEvents;
import com.github.crittscott.somestacks.client.FabricClientRendering;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.minecraft.client.Minecraft;
import net.minecraft.world.InteractionResult;

/**
 * Registers the development-only /ssclienttest local callback smoke command; see {@link
 * ClientCallbackChecks#run} for how to run it. Fabric's {@code UseBlockCallback} returns one {@link
 * InteractionResult} with no separate block-use and item-use denial, so this reports only whether
 * the callback consumed the click.
 */
public final class ClientCallbackGameTests implements ClientModInitializer {
    // spotless:off
    @Override
    public void onInitializeClient() {
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) ->
                dispatcher.register(ClientCommandManager.literal("ssclienttest").executes(ctx ->
                        ClientCallbackChecks.run(FabricClientRendering.STACK_MODE_KEY.isDown(), (hand, hit) -> {
                            Minecraft client = Minecraft.getInstance();
                            return ClientCallbackChecks.Result.consumedOnly(
                                    FabricClientEvents.onUseBlock(client.player, client.level, hand, hit)
                                            == InteractionResult.SUCCESS);
                        }))));
    }
    // spotless:on
}
