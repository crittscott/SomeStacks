package com.github.crittscott.somestacks.gametest;

import com.github.crittscott.somestacks.client.FabricClientEvents;
import com.github.crittscott.somestacks.client.FabricKeyMappings;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.minecraft.client.Minecraft;
import net.minecraft.world.InteractionResult;

/** Registers the development-only /ssclienttest local callback smoke command. */
public final class ClientCallbackGameTests implements ClientModInitializer {
    @Override public void onInitializeClient() {
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) ->
                dispatcher.register(ClientCommandManager.literal("ssclienttest").executes(ctx ->
                        ClientCallbackChecks.run(FabricKeyMappings.STACK_MODE_KEY.isDown(), (hand, hit) -> {
                            Minecraft client = Minecraft.getInstance();
                            boolean consumed = FabricClientEvents.onUseBlock(client.player, client.level, hand, hit)
                                    == InteractionResult.SUCCESS;
                            return new ClientCallbackChecks.Result(consumed, consumed, consumed);
                        }))));
    }
}
